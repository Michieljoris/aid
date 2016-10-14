(ns aid.core
  (:require

   [mount.core :as mount :refer [defstate]]
            
   [logging :refer [with-logging-status]]
   
   [taoensso.timbre :as timbre
    :refer (log  trace  debug  info  warn  error  fatal  report color-str
                 logf tracef debugf infof warnf errorf fatalf reportf
                 spy get-env log-env)]
            
   ;; https://github.com/xsc/jansi-clj
   ;; (colors)
   ;; ;; => (:black :default :magenta :white :red :blue :green :yellow :cyan)

   ;; For each color, there exist four functions, e.g. red (create a
   ;; string with red foreground), red-bright (create a string with
   ;; bright red foreground), as well as red-bg (red background) and
   ;; red-bg-bright.
   ;; (attributes
   ;; (:underline-double :no-negative :no-underline :blink-fast :no-strikethrough
   ;;               :conceal :negative :no-italic :italic :faint :no-conceal :no-bold :no-blink
   ;;               :strikethrough :blink-slow :bold :underline)
   [jansi-clj.core :refer :all :exclude [reset]]
   [clojure.pprint :refer (pprint)]
   )
  )

(pprint "hello")
