;; Database
(def qdb (atom
          {:layers [  ;; layer 1
                    {:storage ;protocol of get-entity, write-entity, drop-entity)
                     {:some-id {:id :some-id ;entity
                                :attrs {:some-name {:name :some-name
                                                    :value :some-value
                                                    :ts :some-timestamp ;-1 on creation
                                                    :prev-ts :some-previous-timestamp ;-1 on creation
                                                    ;; meta: {:type :number ;or db/ref, or ???
                                                    ;;        :cardinality :db/single ;or db/multiple
                                                    ;;        }
                                                    }}
                                } 
                      }
                     :VAET {:some-value {:some-attr [{:id :some-ent-id :attrs { }}]}}
                     :AVET {:some-attr-name {:some-value [{:id :some-ent-id :attrs { }}]}}
                     :VEAT {:some-value {:some-ent-id [{:name :some-attr-name :value :etc}]}}
                     :EAVT {:some-ent-id {:some-attr-name {:name :some-attr-name :value :etc}}}
                     }
                    ;; layer 2
                    {:storage ;protocol of get-entity, write-entity, drop-entity)
                     {:some-id {:id :some-id2 ;entity
                                 :attrs {:some-attr-name2 {:name :some-attr-name2
                                                           :value :some-value2
                                                           :ts :some-timestamp2 ;-1 on creation
                                                           :prev-ts :some-previous-timestamp2 ;-1 on creation
                                                           ;; meta: {:type :number ;or db/ref, or ???
                                                           ;;        :cardinality :db/single ;or db/multiple
                                                           ;;        }
                                                           }}
                                 } 
                      }
                     :VAET {}
                     :AVET {}
                     :VEAT {}
                     :EAVT {}}
                    ;; layer 3
                    ;; etc
                    ]
           :top-id 0                    ;highest used id
           :curr-time 0                 ;index of current layer
           }))

