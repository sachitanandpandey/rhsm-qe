(ns com.redhat.qe.sm.gui.tests.subscribe-tests
  (:use [test-clj.testng :only (gen-class-testng data-driven)]
        [com.redhat.qe.sm.gui.tasks.test-config :only (config
                                                       clientcmd)]
        [com.redhat.qe.verify :only (verify)]
        [error.handler :only (with-handlers handle ignore recover)]
        [clojure.contrib.string :only (split)]
        gnome.ldtp)
  (:require [com.redhat.qe.sm.gui.tasks.tasks :as tasks]
            [com.redhat.qe.sm.gui.tasks.candlepin-tasks :as ctasks]
             com.redhat.qe.sm.gui.tasks.ui)
  (:import [org.testng.annotations BeforeClass BeforeGroups Test DataProvider]))

(defn ^{BeforeClass {:groups ["setup"]}}
  register [_]
  (tasks/register-with-creds))

(defn subscribe_all
  "Subscribes to everything available"
  []
  (tasks/search)
  (tasks/do-to-all-rows-in :all-subscriptions-view 1
                           (fn [subscription]
                                (with-handlers [(ignore :item-not-available)
                                                (handle :wrong-consumer-type [e]
                                                        (recover e :log-warning))]
                                  (tasks/subscribe subscription)))
                           :skip-dropdowns? true))

(defn unsubscribe_all 
  "Unsubscribes from everything available"
  []
  (tasks/ui selecttab :my-subscriptions)
  (tasks/do-to-all-rows-in :my-subscriptions-view 0
                           (fn [subscription] (with-handlers [(ignore :not-subscribed)]
                                               (tasks/unsubscribe subscription)
                                               (verify (= (tasks/ui rowexist? :my-subscriptions-view subscription) false))))
                           :skip-dropdowns? true))


(defn ^{Test {:groups ["subscribe"]
              :dataProvider "subscriptions"}}
  subscribe_each [_ subscription]
  (with-handlers [(ignore :item-not-available)
                  (handle :wrong-consumer-type [e]
                          (recover e :log-warning))]
    (tasks/subscribe subscription)))


(defn ^{Test {:groups ["subscribe"]
              :dataProvider "subscribed"}}
  unsubscribe_each [_ subscription]
  (tasks/ui selecttab :my-subscriptions)
  (with-handlers [(ignore :not-subscribed)]
    (tasks/unsubscribe subscription)
    (verify (= false (tasks/ui rowexist? :my-subscriptions-view subscription)))))


(defn ^{Test {:groups ["subscribe" "blockedByBug-703920"]
              :dataProvider "subscriptions"}}
  check_contract_selection_dates
  "https://bugzilla.redhat.com/show_bug.cgi?id=703920"
  [_ subscription]
  (with-handlers [(ignore :item-not-available)
                  (handle :wrong-consumer-type [e]
                          (recover e :log-warning))]
    (tasks/open-contract-selection subscription)
    (try
      (loop [row (- (tasks/ui getrowcount :contract-selection-table) 1)]
        (if (>= row 0)
          (let [startdate (tasks/ui getcellvalue :contract-selection-table row 3)
                enddate (tasks/ui getcellvalue :contract-selection-table row 4)]
            (verify (not (nil? (re-matches #"\d+/\d+/\d+" startdate))))
            (verify (not (nil? (re-matches #"\d+/\d+/\d+" enddate))))
            (recur (dec row)))))
      (finally 
       (tasks/ui click :cancel-contract-selection)))))


(defn ^{Test {:groups ["subscribe" "blockedByBug-723248"]
              :dataProvider "subscriptions"}}
  check_quantity_scroller
  "https://bugzilla.redhat.com/show_bug.cgi?id=723248#c3"
  [_ subscription]
  (with-handlers [(ignore :subscription-not-available)
                  (handle :wrong-consumer-type [e]
                          (recover e :log-warning))]
    (tasks/open-contract-selection subscription)
    (loop [row (- (tasks/ui getrowcount :contract-selection-table) 1)]
      (if (>= row 0)
        (let [contract (tasks/ui getcellvalue :contract-selection-table row 1)
              pool (ctasks/get-pool-id (@config :username)
                                      (@config :password)
                                      (@config :owner-key)
                                      subscription
                                      contract)
              usedmax (tasks/ui getcellvalue :contract-selection-table row 2)
              default (tasks/ui getcellvalue :contract-selection-table row 5)
              used (first (split #" / " usedmax))
              max (last (split #" / " usedmax))
              available (str (- (Integer. max) (Integer. used)))
              cmd (fn [num]
                    (str  "<right> <right> <right> <right> <right> <space> " num " <enter>"))]
          (if (ctasks/multi-entitlement? (@config :username) (@config :password) pool)
            (do
              ;verify that the quantity can be changed
              (tasks/ui selectrowindex :contract-selection-table row)
              (tasks/ui generatekeyevent (cmd available))
              (verify (= available
                         (tasks/ui getcellvalue :contract-selection-table row 5)))
              ;verify that the quantity cannot exceed the max
              (tasks/ui generatekeyevent (cmd (str (+ 1 (Integer. available)))))
              (verify (>= (Integer. available)
                          (Integer. (tasks/ui getcellvalue :contract-selection-table row 5))))
              ;need to verify max and min values when bug has been resolved
              )
            (do
              ;verify that the quantity cannot be changed
              (tasks/ui selectrowindex :contract-selection-table row)
              (tasks/ui generatekeyevent (cmd max))
              (verify (= default
                        (tasks/ui getcellvalue :contract-selection-table row 5)))))
          (recur (dec row)))))
    (tasks/ui click :cancel-contract-selection)))
 
(defn ^{Test {:groups ["subscribe" "blockedByBug-723248"]
              :dataProvider "multi-entitle"}}
  check_quantity_subscribe
  "https://bugzilla.redhat.com/show_bug.cgi?id=723248#c3"
  [_ subscription contract]
  (with-handlers [(ignore :subscription-not-available)
                  (handle :wrong-consumer-type [e]
                          (recover e :log-warning))]
    (tasks/open-contract-selection subscription)
    (tasks/ui selectrow :contract-selection-table contract)
    (let [line (tasks/ui gettablerowindex :contract-selection-table contract)
          usedmax (tasks/ui getcellvalue :contract-selection-table line 2)
          used (first (split #" / " usedmax))
          max (last (split #" / " usedmax))
          available (str (- (Integer. max) (Integer. used)))
          cmd (fn [num]
                (str  "<right> <right> <right> <right> <right> <space> " num " <enter>"))]
      (tasks/ui generatekeyevent (cmd available))
      (tasks/ui click :subscribe-contract-selection)
      (tasks/checkforerror)
      (tasks/wait-for-progress-bar)
      (tasks/ui selecttab :my-subscriptions)
      (let [row (tasks/ui gettablerowindex :my-subscriptions-view subscription)
            count (tasks/ui getcellvalue :my-subscriptions-view row 3)]
        (verify (= count available))))
    (tasks/unsubscribe subscription)))

(defn ^{Test {:groups ["subscribe" "blockedByBug-688454"]}}
  check_blank_date [_]
  (tasks/ui selecttab :all-available-subscriptions)
  (tasks/ui settextvalue :date-entry "")
  (let [error (with-handlers [(handle :date-error [e]
                                      (:type e))]
                (do (tasks/ui click :search)
                    (tasks/checkforerror)))]
    (verify (= :date-error error)))
  (let [date (tasks/ui gettextvalue :date-entry)
        systemtime (.trim (.getStdout (.runCommandAndWait @clientcmd "date +%m/%d/%Y")))]
    (verify (not (nil? (re-matches #"\d{2}/\d{2}/\d{4}" date))))
    (verify (= date systemtime))))

(comment
(defn ^{Test {:groups ["subscribe" "blockedByBug-740831"]}}
  check_subscribe_greyout [_]
  ))

;; you can test data providers in a REPL the following way:
;; (doseq [s (stest/get_subscriptions nil :debug true)]
;;   (stest/subscribe_each nil (peek s)))

(defn ^{DataProvider {:name "multi-entitle"}}
  get_multi_entitle_subscriptions [_ & {:keys [debug]
                                        :or {debug false}}]
  (tasks/restart-app)
  (register nil)
  (tasks/search)
  (let [subs (atom [])
        subscriptions (tasks/get-table-elements
                       :all-subscriptions-view
                       0
                       :skip-dropdowns? true)]
    (doseq [s subscriptions]
      (with-handlers [(ignore :item-not-available)
                      (handle :wrong-consumer-type [e]
                              (recover e :log-warning))]
        (tasks/open-contract-selection s)
        (loop [row (- (tasks/ui getrowcount :contract-selection-table) 1)]
          (if (>= row 0)
            (let [contract (tasks/ui getcellvalue :contract-selection-table row 1)
                  pool (ctasks/get-pool-id (@config :username)
                                          (@config :password)
                                          (@config :owner-key)
                                          s
                                          contract)]
              (if (ctasks/multi-entitlement? (@config :username) (@config :password) pool)
                (swap! subs conj [s contract]))
              (recur (dec row)))))
        (tasks/ui click :cancel-contract-selection)))
    (if-not debug
      (to-array-2d @subs)
      @subs)))

(defn ^{DataProvider {:name "subscriptions"}}
  get_subscriptions [_ & {:keys [debug]
                          :or {debug false}}]
  (tasks/restart-app)
  (register nil)
  (tasks/search)
  (let [subs (into [] (map vector (tasks/get-table-elements
                                   :all-subscriptions-view
                                   0
                                   :skip-dropdowns? true)))] 
    (if-not debug
      (to-array-2d subs)
      subs)))

(defn ^{DataProvider {:name "subscribed"}}
  get_subscribed [_ & {:keys [debug]
                       :or {debug false}}]
  (tasks/restart-app)
  (register nil)
  (tasks/ui selecttab :my-subscriptions)
  (subscribe_all)
  (tasks/ui selecttab :my-subscriptions)
  (let [subs (into [] (map vector (tasks/get-table-elements
                                   :my-subscriptions-view
                                   0
                                   :skip-dropdowns? true)))]
    (if-not debug
      (to-array-2d subs)
      subs)))

(defn ^{DataProvider {:name "multi-contract"}}
  get_multi_contract_subscriptions [_ & {:keys [debug]
                                         :or {debug false}}]
  (tasks/restart-app)
  (register nil)
  (tasks/search {:do-not-overlap? false})
  (let [subs (atom [])
        allsubs (tasks/get-table-elements :all-subscriptions-view 0 :skip-dropdowns? true)]
    (doseq [s allsubs]
      (with-handlers [(ignore :item-not-available)
                      (handle :contract-selection-not-available [e]
                              (tasks/unsubscribe s))]
        (tasks/open-contract-selection s)0
        (tasks/ui click :cancel-contract-selection)
        (swap! subs conj [s])))
    (if-not debug
      (to-array-2d @subs)
      @subs)))

  ;; TODO https://bugzilla.redhat.com/show_bug.cgi?id=683550
  ;; TODO https://bugzilla.redhat.com/show_bug.cgi?id=691784
  ;; TODO https://bugzilla.redhat.com/show_bug.cgi?id=691788
  ;; TODO https://bugzilla.redhat.com/show_bug.cgi?id=727631

(gen-class-testng)
