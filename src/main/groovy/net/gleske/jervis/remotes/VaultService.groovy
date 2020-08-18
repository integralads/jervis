package net.gleske.jervis.remotes

import net.gleske.jervis.remotes.interfaces.TokenCredential
import net.gleske.jervis.exceptions.JervisException

class VaultService implements SimpleRestServiceSupport {
    private final String vault_url
    private final TokenCredential credential
    Map mountVersions = [:]

    VaultService(String vault_url, TokenCredential credential) {
        this.vault_url = (vault_url[-1] == '/')? vault_url : vault_url + '/'
        if(!this.vault_url.endsWith('v1/')) {
            this.vault_url += 'v1/'
        }
        this.credential = credential
    }

    String baseUrl() {
        this.vault_url
    }

    Map header(Map headers = [:]) {
        headers['X-Vault-Token'] = credential.getToken()
        headers
    }

    /*
       Get secret from a KV v1 or v2 secret engine.
      */
    Map getSecret(String path, int version = 0) {
        String mount = path -~ '/.*$'
        String subpath = path -~ '^[^/]+/'
        if(isKeyValueV2(mount)) {
            apiFetch("${mount}/data/${subpath}?version=${version}")?.data?.data
        }
        else {
            apiFetch(path)
        }
    }

    void setSecret(String path, Map secret) {
        String mount = path -~ '/.*$'
        String subpath = path -~ '^[^/]+/'
        if(isKeyValueV2(mount)) {
            apiFetch("${mount}/data/${subpath}?version=${version}")?.data?.data
        }
        else {
            apiFetch(path, [:], 'POST', objToJson(secret))
        }
    }

    void setMountVersions(String mount, def version) {
        if(!(version in ['1', '2'])) {
            throw new JervisException('Error: Vault key-value mounts can only be version "1" or "2" (String).')
        }
        this.mountVersions[mount] = version
    }

    void setMountVersions(Map mountVersions) {
        mountVersions.each { k, v ->
            this.setMountVersions(k, v)
        }
    }

    /**
      Checks version of a Key-Value engine mount.  Returns true if Key-Value v2
      or false if Key-Value v2.
      */
    private Boolean isKeyValueV2(String mount) {
        discoverMountVersion(mount)
        this.mountVersions[mount] == '2'
    }

    /**
      If the mount is not already in mountVersions, then this will inspect the
      mount and set whether the mount is a Key-Value secret engine v1 or v2.
      */
    private void discoverMountVersion(String mount) {
        if(mount in this.mountVersions) {
            return
        }
        setMountVersions(mount, apiFetch("sys/mounts/${mount}/tune")?.options?.version)
    }

    List listPath(String path) {
        String mount = path -~ '/.*$'
        String subpath = path -~ '^[^/]+/'
        subpath = (!subpath || subpath[-1] == '/')? subpath : subpath + '/'

        if(isKeyValueV2(mount)) {
            apiFetch("${mount}/metadata/${subpath}?list=true")?.data?.keys
        }
        else {
            // KV v1 API call here
            apiFetch("${mount}/${subpath}?list=true")?.data?.keys
        }
    }

    /**
      Internal method used by findAllKeys() which tracks recursion level in addition to the user-passed settings.
      */
    private List recursiveFindAllKeys(String path, Integer desiredLevel, Integer level) {
        if(desiredLevel > 0 && level > desiredLevel) {
            return []
        }
        List entries = listPath(path).collect { String key ->
            if(key.endsWith('/')) {
                recursiveFindAllKeys(path + key, desiredLevel, level + 1)
            }
            else {
                [path + key]
            }
        }

        if(entries) {
            entries.sum()
        }
        else {
            []
        }
    }

    /**
      Recursively traverses the path for subkeys.  If level is 0 then there's
      no depth limit.  When level = n, keys are traversed up to the limit.
       */
    List findAllKeys(String path, Integer level = 0) {
        recursiveFindAllKeys(path, level, 1)
    }

    /**
      Copies the contents of secret from a source key, srcKey, to the
      destination key, destKey.
      */
    void copySecret(String srcKey, String destKey) {
        setSecret(destKey, getSecret(srcKey))
    }

    /**
      Recursively copies all secrets from the source path, srcPath, to the
      destination path, destPath.
      */
    void copyAllKeys(String srcPath, destPath, Integer level = 0) {
    }
}