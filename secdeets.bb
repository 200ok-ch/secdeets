#!/usr/bin/env bb

(ns secdeets
  (:require [shell-smith.core :as smith]
            [babashka.process :as process]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def usage "
secdeets

Usage:
  secdeets <detail> [--db=<db>] [--password-file=<password-file>] [--entry=<entry>]
  secdeets --help
  secdeets --version
Options:
  -d --db=<db>                        Path to the keepassxc db.
  -p --password-file=<password-file>  Path to a file containing the password of the keepassxc db.
  -e --entry=<entry>                  Name of the entry in keepassxc db.
  -h --help                           Show help (this).
  -v --version                        Show version.

----
secdeets is a thin wrapper around keepassxc-cli to query security
details in a more scriptable way. It is fully configurable via
config file, or env vars.

Some details better go into env vars as they are more /global/, e.g.

```
export SECDEETS_DB=~/secrets/db.kdbx
export SECDEETS_PASSWORD_FILE=~/secrets/keepassxc
```

for some a config file might be a better option as they might
depend on the context, e.g.

```
# secdeets.yml
---
entry: 'The name of some entry in your db'
```

And all can be overriden by command line args, e.g.

```
secdeets password --entry MyVPN
```

Subcommands (aka detail) is one of: username, password, totp, full

The subcommand full returns JSON, all other commands plain text.")

(def ^:dynamic *config*)

(defn add-password [config]
  (->> config
       ;; FIXME: why the underscore
       :password_file
       slurp
       (assoc config :password)))

(defn keepassxc-cli [opts]
  (let [{:keys [password db entry]} *config*]
    (->> ["keepassxc-cli show -s"
          (str/split opts #" ")
          db entry]
         flatten
         (apply process/sh {:in password})
         :out
         str/trim)))

(defn username []
  (keepassxc-cli "-a username"))

(defn password []
  (keepassxc-cli "-a password"))

(defn totp []
  (keepassxc-cli "-t"))

;; for the repl
#_(alter-var-root (var *config*) (constantly (config)))

(defn -main [& args]
  (binding [*config* (add-password (smith/config usage))]

    (cond
      (:help *config*)
      (println usage)

      (:version *config*)
      (println "secdeets 0.1.0 (FNORD)")

      (-> *config* :detail #{"username"})
      (println (username))

      (-> *config* :detail #{"password"})
      (println (password))

      (-> *config* :detail #{"totp"})
      (println (totp))

      (-> *config* :detail #{"full"})
      (println (json/generate-string {:username (username)
                                      :password (password)
                                      :totp (totp)}))

      :else
      (do
        (println "Something is off. This is the config:")
        (prn *config*)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
