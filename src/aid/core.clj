(ns aid.core
  (:require

   [com.rpl.specter :refer [transform select setval filterer walker view collect-one
                            select-one must
                            ALL MAP-VALS ATOM FIRST]]
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
   [clojure.pprint :refer (pprint)]))

(defn make-db [{:keys [layers top-id curr-time]}]
  {:layers layers :top-id top-id :curr-time curr-time})

(defn make-layer [{:keys [storage VAET AVET AEVT VEAT EAVT EVAT]}]
  {:storage storage :VAET VAET :AVET AVET :VEAT VEAT :EAVT EAVT}) ;; :AEVT AEVT :EVAT EVAT

(defn make-entity
  ([] (make-entity :db/no-id-yet))
  ([id] {:id {}}))

(defn make-attr
  ([name value type] (make-attr name value type nil))
  ([name value type {:keys [cardinality] :or {cardinality :db/single}}]
   {:pre [(contains? #{:db/single :db/multiple} cardinality)]}
   (with-meta {:name name :value value :ts -1 :prev-ts -1} {:type type :cardinality cardinality})))

(defn add-attr [ent attr]
  (let [attr-id (keyword (:name attr))]
    (assoc-in ent [:attrs attr-id] attr)))

(defprotocol Storage
  (get-entity [storage e-id] )
  (write-entity [storage entity])
  (drop-entity [storage entity]))

(defrecord InMemory []
  Storage
  (get-entity [storage e-id] (e-id storage))
  (write-entity [storage entity] (assoc storage (:id entity) entity))
  (drop-entity [storage entity] (dissoc storage (:id entity))))

(defn make-in-memory-storage [] (InMemory.))

(defn indices [] [:VAET :AVET :VEAT :EAVT]) ;; :AEVT :EVAT

(def attr-reorder-map
  {:to   {:VAET #(vector %3 %2 %1) :AVET #(vector %2 %3 %1) :VEAT #(vector %3 %1 %2) :EAVT #(vector %1 %2 %3)}
   :from {:VAET #(vector %3 %2 %1) :AVET #(vector %3 %1 %2) :VEAT #(vector %2 %3 %1) :EAVT #(vector %1 %2 %3)}})

(defn reorder-eav-fn
"Returns function that transforms an attribute vector to/from the order of
  the attributes of the index"
  [index from-or-to]
  (get-in attr-reorder-map [from-or-to (:type (meta index))])) 

(defn from-eav [index] (reorder-eav-fn index :from)) 
(defn to-eav [index] (reorder-eav-fn index :to)) 

(defn usage-pred [index] (:usage-pred (meta index)))

(defn single? [attr] (= :db/single (:cardinality (meta attr))))

(defn make-index
  [{:keys [type usage-pred] :or {:usage-pred (constantly true)}}]
  (with-meta {} {:type type :usage-pred usage-pred}))

(defn make-db-atom []
   (atom 
    (make-db {:layers [(make-layer
                        {:storage {:foo :bar} ;(make-in-memory-storage) ; empty map (record with Storage protocol)
                         :VAET (make-index {:type :VAET
                                            :usage-pred (fn [attr] (= :db/ref (:type (meta attr))))}) ;empty map
                         :AVET (make-index {:type :AVET})
                         :VEAT (make-index {:type :VEAT})
                         :EAVT (make-index {:type :EAVT})
                         ;; :AEVT (make-index :AEVT)
                         ;; :EVAT (make-index :EVAT)
                         })
                       (make-layer
                        {:storage {:foo2 :bar2} ;(make-in-memory-storage) ; empty map (record with Storage protocol)
                         :VAET (make-index {:type :VAET
                                            :usage-pred (fn [attr] (= :db/ref (:type (meta attr))))}) ;empty map
                         :AVET (make-index {:type :AVET})
                         :VEAT (make-index {:type :VEAT})
                         :EAVT (make-index {:type :EAVT})
                         ;; :AEVT (make-index :AEVT)
                         ;; :EVAT (make-index :EVAT)
                         })
                       ]
              :top-id 0
              :curr-time 0})))

;; (pprint @(make-db))
;; read ****************************************************************************************************
(defn entity-aty
   ([db ent-id] (entity-at db (:curr-time db) ent-id))
  ([db ts ent-id]
   ;; (get-entity (get-in db [:layers ts :storage]) ent-id)
   (select-one [:layers (srange ts (inc ts)) FIRST :storage #(get-entity % ent-id)] db)
   ))

(defn attr-at
   ([db ent-id attr-name] (attr-at db ent-id attr-name (:curr-time db)))
   ([db ent-id attr-name ts] (get-in (entity-at db ts ent-id) [:attrs attr-name])))

(defn value-of-at                       ;value-of-attr
   ([db ent-id attr-name]  (:value (attr-at db ent-id attr-name)))
   ([db ent-id attr-name ts] (:value (attr-at db ent-id attr-name ts))))

(defn indx-at
   ([db kind] (indx-at db kind (:curr-time db)))
   ([db kind ts] (kind ((:layers db) ts))))

(do
  (defn evolution-of [db ent-id attr-name]
    (reverse (select [:layers ALL :storage (must ent-id) :attrs (must attr-name) :value] @qdb))
    ;; (loop [res [] ts (:curr-time db)]
    ;;   (if (= -1 ts) (reverse res)
    ;;       (let [attr (attr-at db ent-id attr-name ts)]
    ;;         (recur (conj res {(:ts attr) (:value attr)})  (:prev-ts attr)))))
    )

  ;; (pprint (select [:layers ALL :storage (must :some-id) :attrs (must :some-name) :value] @qdb))
  (evolution-of @qdb :some-id :some-name ))

;; add entity ****************************************************************************************************
(defn- next-ts
  "Returns curr-time of database, increased by one"
  [db] (inc (:curr-time db)))

(do
  (defn- update-creation-ts
    "Sets timestamp (ts) of all attributes of entity to ts-val"
    [ent ts-val]
    (transform [:attrs MAP-VALS] #(assoc % :ts ts-val) ent)
    ;; (reduce #(assoc-in %1 [:attrs %2 :ts ] ts-val) ent (keys (:attrs ent)))
    )
  ;; (update-creation-ts {:id 1 :attrs {:a {:ts 1 :name :a} :b {:ts 2 :name :b}}} :foo)
  )

(defn- next-id
  "Returns a tuple of entity id and latest used id in db. Entity id
  will be same as this id if it is currently set to :db/no-id-yet"
  [db ent]
   (let [top-id (:top-id db)
         ent-id (:id ent)
         increased-id (inc top-id)]
         (if (= ent-id :db/no-id-yet)
             [(keyword (str increased-id)) increased-id]
             [ent-id top-id])))

(defn- fix-new-entity
  "Give ent a unused id if it has'nt got one already, and sets ts of
  all its attributes to the db.curr-time +1. Returns tuple of entity
  and the top-id in the db"
  [db ent]
   (let [[ent-id next-top-id] (next-id db ent)
         new-ts               (next-ts db)]
       [(update-creation-ts (assoc ent :id ent-id) new-ts) next-top-id]))

(defn- update-entry-in-index
  [index path operation]
   (let [update-path (butlast path)
         update-value (last path)
         to-be-updated-set (get-in index update-path #{})]
     (assoc-in index update-path (conj to-be-updated-set update-value))))

(defn collify [x] (if (coll? x) x [x]))

(defn- update-attr-in-index [index ent-id attr-name target-val operation]
   (let [colled-target-val (collify target-val)
         update-entry-fn (fn [ind vl] 
                             (update-entry-in-index 
                                ind 
                                ((from-eav index) ent-id attr-name vl) 
                                operation))]
     (reduce update-entry-fn index colled-target-val)))

(defn- add-entity-to-index [ent layer index-name]
   (let [ent-id (:id ent)
         index (index-name layer)       ;(get layer index-name)
         all-attrs  (vals (:attrs ent))
         relevant-attrs (filter #((usage-pred index) %) all-attrs)
         add-in-index-fn (fn [index attr] 
                           (update-attr-in-index index ent-id (:name attr) 
                                                 (:value attr) 
                                                 :db/add))]
        (assoc layer index-name (reduce add-in-index-fn index relevant-attrs))))


(defn add-entity [db ent]
   (let [[fixed-ent next-top-id] (fix-new-entity db ent)
         layer-with-updated-storage (update-in 
                            (last (:layers db)) [:storage] write-entity fixed-ent)
         add-fn (partial add-entity-to-index fixed-ent)
         new-layer (reduce add-fn layer-with-updated-storage (indices))]
    (assoc db :layers (conj (:layers db) new-layer) :top-id next-top-id)))

(defn add-entities [db ents-seq] (reduce add-entity db ents-seq))

;; Update entity  ************************************************************************************************

(defn- update-attr-modification-time  
  [attr new-ts]
       (assoc attr :ts new-ts :prev-ts (:ts attr)))

(defn- update-attr-value [attr value operation]
   (cond
      (single? attr)    (assoc attr :value #{value})
      ; now we're talking about an attribute of multiple values
      (= :db/reset-to operation) 
        (assoc attr :value value)
      (= :db/add operation) 
        (assoc attr :value (clojure.set/union (:value attr) value))
      (= :db/remove operation)
        (assoc attr :value (clojure.set/difference (:value attr) value))))

(defn- update-attr [attr new-val new-ts operation]
    {:pre  [(if (single? attr)
            (contains? #{:db/reset-to :db/remove} operation)
            (contains? #{:db/reset-to :db/add :db/remove} operation))]}
    (-> attr
       (update-attr-modification-time new-ts)
       (update-attr-value new-val operation)))

(defn- remove-entry-from-index [index path]
  (let [path-head (first path)
        path-to-items (butlast path)
        val-to-remove (last path)
        old-entries-set (get-in index path-to-items)]
    (cond
     (not (contains?  old-entries-set val-to-remove)) index ; the set of items does not contain the item to remove, => nothing to do here
     (= 1 (count old-entries-set))  (update-in index [path-head] dissoc (second path)) ; a path that splits at the second item - just remove the unneeded part of it
     :else (update-in index path-to-items disj val-to-remove))))

(defn- remove-entries-from-index [ent-id operation index attr]
  (if (= operation :db/add)
       index
      (let  [attr-name (:name attr)
             datom-vals (collify (:value attr))
             paths (map #((from-eav index) ent-id attr-name %) datom-vals)]
       (reduce remove-entry-from-index index paths))))

(defn- update-index [ent-id old-attr target-val operation layer ind-name]
  (if-not ((usage-pred (get-in layer [ind-name])) old-attr)
    layer
    (let [index (ind-name layer)
          cleaned-index (remove-entries-from-index  ent-id operation index old-attr)
          updated-index  ( if  (= operation :db/remove)
                                         cleaned-index
                                         (update-attr-in-index cleaned-index ent-id  (:name old-attr) target-val operation))]
      (assoc layer ind-name updated-index))))

(defn- put-entity [storage e-id new-attr]
  (assoc-in (get-entity storage e-id) [:attrs (:name new-attr)] new-attr))

(defn- update-layer
  [layer ent-id old-attr updated-attr new-val operation]
  (let [storage (:storage layer)
        new-layer (reduce (partial update-index  ent-id old-attr new-val operation) layer (indices))]
    (assoc new-layer :storage (write-entity storage (put-entity storage ent-id updated-attr)))))

(defn update-entity
   ([db ent-id attr-name new-val]
    (update-entity db ent-id attr-name new-val :db/reset-to))
   ([db ent-id attr-name new-val operation]
      (let [update-ts (next-ts db)
            layer (last (:layers db))
            attr (attr-at db ent-id attr-name)
            updated-attr (update-attr attr new-val update-ts operation)
            fully-updated-layer (update-layer layer ent-id 
                                              attr updated-attr 
                                              new-val operation)]
        (update-in db [:layers] conj fully-updated-layer))))


;; Remove entity *************************************************************************************************

(defn- reffing-to [e-id layer]
   (let [vaet (:VAET layer)]
         (for [[attr-name reffing-set] (e-id vaet)
               reffing reffing-set]
              [reffing attr-name])))


(defn- remove-back-refs [db e-id layer]
   (let [reffing-datoms (reffing-to e-id layer)
         remove-fn (fn[d [e a]] (update-entity db e a e-id :db/remove))
         clean-db (reduce remove-fn db reffing-datoms)]
     (last (:layers clean-db))))

(defn- remove-entity-from-index [ent layer ind-name]
  (let [ent-id (:id ent)
        index (ind-name layer)
        all-attrs  (vals (:attrs ent))
        relevant-attrs (filter #((usage-pred index) %) all-attrs )
        remove-from-index-fn (partial remove-entries-from-index  ent-id  :db/remove)]
    (assoc layer ind-name (reduce remove-from-index-fn index relevant-attrs))))

(defn remove-entity [db ent-id]
   (let [ent (entity-at db ent-id)
         layer (remove-back-refs db ent-id (last (:layers db)))
         no-ref-layer (update-in layer [:VAET] dissoc ent-id)
         no-ent-layer (assoc no-ref-layer :storage 
                                   (drop-entity  
                                          (:storage no-ref-layer) ent))
         new-layer (reduce (partial remove-entity-from-index ent) 
                                 no-ent-layer (indices))]
     (assoc db :layers (conj  (:layers db) new-layer))))


;; Test --------------------------------------------------------------------------------
;; (def db (make-db-atom))

;; (def storage (InMemory.))
;; (def attr (make-attr :my-attr 1 :number))
;; (def entity (make-entity :my-entity))

;; (let [entity (add-attr entity attr)
;;       storage (write-entity storage entity)
;;       storage (drop-entity storage entity)
;;       ]
;;   (get-entity storage :my-entity)
;;   )


;; (Database. 1 2 3 )
;; (Entity. 1 {})
;; (def l (Layer. 1 2 3 4 5))
;; (keys l)
;; (instance? Layer l)
;; (make-entity 1)
;; (meta (make-attr :foo 1 :number {:foo :db/multiple}))

(def storage (make-in-memory-storage))
(def qdb (atom
          {:layers [  ;; layer 1
                    {:storage
                     (write-entity storage
                                   {:id :some-id ;entity
                                    :attrs {:some-name {:name :some-name
                                                        :value :some-value
                                                        :ts :some-timestamp ;-1 on creation
                                                        :prev-ts :some-previous-timestamp ;-1 on creation
                                                        ;; meta: {:type :number ;or db/ref, or ???
                                                        ;;        :cardinality :db/single ;or db/multiple
                                                        ;;        }
                                                        }}})
                     :VAET {} :AVET {} :VEAT {} :EAVT {}
                     } 
                    ;; layer 2
                    {:storage ;protocol of get-entity, write-entity, drop-entity)
                     (write-entity storage {:id :some-id ;entity
                                            :attrs {:some-attr-name2 {:name :some-attr-name2
                                                                      :value :some-value2
                                                                      :ts :some-timestamp2 ;-1 on creation
                                                                      :prev-ts :some-previous-timestamp2 ;-1 on creation
                                                                      ;; meta: {:type :number ;or db/ref, or ???
                                                                      ;;        :cardinality :db/single ;or db/multiple
                                                                      ;;        }
                                                                      }}})
                     :VAET {} :AVET {} :VEAT {} :EAVT {}
                     }
                    ;; layer 3
                    ;; etc
                    ]
           :top-id 0                    ;highest used id
           :curr-time 2                 ;index of current layer
           }))
