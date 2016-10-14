;; https://github.com/boot-clj/boot/wiki/Repository-Credentials-and-Deploying
(set-env!
 :dependencies '[[org.clojure/clojure "1.8.0"]
                 ;; Environment
                 [environ "1.1.0"]
                 [boot-environ "1.1.0"]
                 [acyclic/squiggly-clojure "0.1.5"]  
                 ])

(require
 '[environ.boot :refer [environ]]
 ) 

(deftask dev-env
  "Set env for your dev env"
  []
  (merge-env!
   :dependencies '[
                   [acyclic/squiggly-clojure "0.1.5"]  
                   ])
  (environ :env {:clj-env "dev"
                 :squiggly {:checkers [:eastwood :kibit]
                            :eastwood-exclude-linters [:unlimited-use]}          
                 }))

(deftask prod-env
  "Set env for your dev env"
  []
  (merge-env!
   ;; :dependencies '[
   ;;                 [acyclic/squiggly-clojure "0.1.5"]  
   ;;                 [print-foo-cljs "2.0.0"] 
   ;;                 ]
   )
  (environ :env {:clj-env "prod"
                 ;; :squiggly {:checkers [:eastwood :kibit]
                 ;;            :eastwood-exclude-linters [:unlimited-use]}
                 
                 }))
