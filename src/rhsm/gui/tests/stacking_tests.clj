(ns rhsm.gui.tests.stacking_tests
  (:use [test-clj.testng :only (gen-class-testng
                                data-driven)]
        [rhsm.gui.tasks.test-config :only (config
                                           clientcmd)]
        [com.redhat.qe.verify :only (verify)]
        [slingshot.slingshot :only (try+
                                    throw+)]
        [clojure.string :only (split
                               split-lines
                               blank?
                               join
                               trim-newline
                               trim)]
        rhsm.gui.tasks.tools
        gnome.ldtp)
  (:require [rhsm.gui.tasks.tasks :as tasks]
            [rhsm.gui.tests.base :as base]
            [rhsm.gui.tasks.candlepin-tasks :as ctasks]
             rhsm.gui.tasks.ui)
  (:import [org.testng.annotations
            BeforeClass
            AfterClass
            BeforeGroups
            AfterGroups
            Test
            DataProvider]
           org.testng.SkipException
           [com.redhat.qe.auto.bugzilla BzChecker]))

(def prod-dir-atom (atom {}))  ; "ProductCertDir" value from conf file
(def sub (atom {}))            ; used in "random-subscription" func to pic a subscription

(def stacking-dir "/tmp/stacking-dir/")  ; temporary product directory
                                         ; which has stackable products

(defn random-subscription
  "Validates if the subscriptions are stackable by verifying quantity to be greater than 1
   and picks a random subscriptions which can be stacked"
  [subscriptions]
  (let [quantity-list (distinct
                       (into []
                             (map (fn [i] (Integer.
                                          (re-find #"\d+"
                                                   (tasks/ui getcellvalue :all-subscriptions-view
                                                             (tasks/skip-dropdown :all-subscriptions-view i)
                                                             3)))) subscriptions)))]
    (case (into [] quantity-list)
      [] (throw (SkipException. (str "Data unavailable for stacking test")))
      [1] (throw (SkipException. (str "Data unavailable for stacking test")))
      (do (reset! sub (rand-nth subscriptions))
          (while (= "1" (re-find #"\d+"
                                 (tasks/ui getcellvalue :all-subscriptions-view
                                           (tasks/skip-dropdown :all-subscriptions-view @sub)
                                           3)))
            (reset! sub (rand-nth subscriptions)))))
    @sub))

(defn is-date?
  "Verifies if the the object is a valid date object"
  [date]
  (let [split-date (split date #"/")
        count-split-date (count split-date)
        date-obj-len '(2 2 4)]
    (if (= 10 (count date))
      (if (= 3 count-split-date)
        (do
          (doseq [[x y] (map list date-obj-len split-date)]
            (if-not (= (count y) x)
              (let [result false]
                result))) true) false) false)))

(defn quantiy-list-equal?
  "Verifies if the quanity string lists before and after subscribing are equal for
  stackable subscriptions in the same group"
  [a b]
  (let [a-int (into [] (map #(Integer. (re-find  #"\d+" %)) a))
        b-int (into [] (map #(Integer. (re-find  #"\d+" %)) b))
        equal (map (fn [i j] (> i j)) a-int  b-int)]
    (if (some #(= false %) equal) false true)))

(defn validate-subscriptions
  "Validates if the subscriptions are stackable by verifying quantity to be greater than 1"
  [subscriptions]
  (let [quantity-list (distinct
                       (into []
                             (map (fn [i] (Integer.
                                          (re-find #"\d+"
                                                   (tasks/ui getcellvalue :all-subscriptions-view
                                                             (tasks/skip-dropdown :all-subscriptions-view i)
                                                             3)))) subscriptions)))]
    (case (into [] quantity-list)
      [] false
      [1] false
      true)))

(defn ^{BeforeClass {:groups ["setup"]}}
  setup [_]
  (try
    (if (= "RHEL7" (get-release)) (base/startup nil))
    (if (bash-bool (:exitcode (run-command (str "test -d " stacking-dir))))
      (run-command (str "rm -rf " stacking-dir)))
    (tasks/restart-app :reregister? true)
    (run-command (str "mkdir " stacking-dir))
    (reset! prod-dir-atom (tasks/conf-file-value "productCertDir"))
    (let [stackable-pems (tasks/get-stackable-pem-files)
          change-prod-dir (tasks/set-conf-file-value "productCertDir" stacking-dir)
          ret-val (fn [pem-file] (:exitcode
                                 (run-command (str "cp  " @prod-dir-atom "/" pem-file "  " stacking-dir))))
          copy-pem (map ret-val stackable-pems)]
      copy-pem)
    (catch Exception e
      (reset! (skip-groups :stacking) true)
      (throw e))))

(defn ^{BeforeGroups {:groups ["stacking"]
                      :value ["stacking-sockets"]}}
  before_sockets_stacking [_]
  (tasks/write-facts "{\"cpu.cpu_socket(s)\": \"20\"}"))

(defn ^{Test {:groups ["stacking"
                       "blockedByBug-854380"]
              :value ["stacking-sockets"]}}
  assert_subscriptions_displayed
  "Asserts the matching subscriptions are displayed when the system is partially subscribed"
  [_]
  (try
    (tasks/restart-app :reregister? true)
    (tasks/search :match-installed? true)
    (let [subscriptions (into [] (tasks/get-table-elements :all-subscriptions-view 0 :skip-dropdown? true))
          sub-type-map (ctasks/build-subscription-attr-type-map)
          socket-subs  (for [s subscriptions
                             :let [x (get sub-type-map s)]
                             :when (some #(= "sockets" %) x)] s)
          rand-sub (random-subscription socket-subs)
          get-quantity (fn [i] (tasks/ui getcellvalue :all-subscriptions-view
                                        (tasks/skip-dropdown :all-subscriptions-view i) 3))
          quantity (get-quantity rand-sub)]
      (tasks/skip-dropdown :all-subscriptions-view rand-sub)
      (tasks/ui generatekeyevent (str
                                  (repeat-cmd 3 "<right> ")
                                  "<space> " "1 " "<enter>"))
      (tasks/subscribe rand-sub)
      (tasks/restart-app)
      (tasks/ui selecttab :all-available-subscriptions)
      (tasks/search :match-installed? true)
      (verify (not (= quantity (get-quantity rand-sub)))))
  (finally
   (tasks/unsubscribe_all))))

(defn ^{Test {:groups ["stacking"
                       "blockedByBug-990639"]
              :value ["stacking-sockets"]}}
  check_dates_partially_subscribed
  "Checks if start and end dates are displayed for partially subscribed poducts"
  [_]
  (try
    (tasks/restart-app :reregister? true)
    (tasks/search :match-installed? true)
    (let [subscriptions (into [] (tasks/get-table-elements :all-subscriptions-view 0 :skip-dropdown? true))
          sub-type-map (ctasks/build-subscription-attr-type-map)
          socket-subs (for [s subscriptions
                            :let [x (get sub-type-map s)]
                            :when (some #(= "sockets" %) x)] s)
          rand-sub (random-subscription socket-subs)]
      (tasks/skip-dropdown :all-subscriptions-view rand-sub)
      (tasks/ui generatekeyevent (str
                                  (repeat-cmd 3 "<right> ")
                                  "<space> " "1 " "<enter>"))
      (tasks/subscribe rand-sub)
      (tasks/ui selecttab :my-installed-products)
      (tasks/do-to-all-rows-in :installed-view 0
                               (fn [sub]
                                 (let [row-num (tasks/skip-dropdown :installed-view sub)]
                                   (if (= (tasks/ui getcellvalue :installed-view row-num 2)
                                          "Partially Subscribed")
                                     (do
                                       (verify (is-date? (tasks/ui getcellvalue
                                                                   :installed-view row-num 3)))
                                       (verify (is-date? (tasks/ui getcellvalue
                                                                   :installed-view row-num 4)))))))))
    (finally
     (tasks/unsubscribe_all))))

(defn get_pem_file_for_subscription
  "This fuction returns pem file for the randomly selected product"
  [existing-prod-dir rand-prod-list]
  (let [raw-pem-files (:stdout (run-command
                                (str "cd " existing-prod-dir
                                     "; for x in `ls`; do rct cat-cert $x | egrep \"Path\" | cut -d: -f 2; done")))
        pem-file-list (into [] (map clojure.string/trim
                                    (clojure.string/split-lines raw-pem-files)))
        raw-prods (:stdout (run-command
                            (str "cd " existing-prod-dir
                                 "; for x in `ls`; do rct cat-cert $x | egrep \"Name\" | cut -d: -f 2; done")))
        prod-list (into [] (map clojure.string/trim (clojure.string/split-lines raw-prods)))
        prod-pem-file-map (zipmap prod-list pem-file-list)
        pem-file (get prod-pem-file-map (first rand-prod-list))]
    pem-file))

(defn create_temp_prod_dir
  "creates a temp dir and moves the apropriate pem file"
  [prod-dir new-prod-dir pem-file]
   (if (not (bash-bool (:exitcode (run-command (str "test -d " new-prod-dir)))))
      (run-command (str "mkdir " new-prod-dir)))
    (run-command (str "cp " prod-dir pem-file "  " new-prod-dir))
    (tasks/set-conf-file-value "productCertDir" new-prod-dir))

(defn ^{Test {:groups ["stacking"
                       "blockedByBug-745965"
                       "blockedByBug-1040119"]
              :value ["stacking-sockets"]}}
  assert_future_cert_status
  "Asserts cert status for future entitilment"
  [_]
  (tasks/restart-app :reregister? true)
  (tasks/ui selecttab :all-available-subscriptions)
  (tasks/search :match-installed? true)
  (let [                                ;getting future date
        date-string (tasks/ui gettextvalue :date-entry)
        date-split (split date-string #"-")
        year (first date-split)
        month (second date-split)
        day (last date-split)
        new-year (+ (Integer. (re-find  #"\d+" year)) 1)

                                        ;creating new directory with single product
        new-prod-dir (str "/tmp/single-pem/")
        existing-prod-dir (tasks/conf-file-value "productCertDir")
        subscriptions (into [] (tasks/get-table-elements :all-subscriptions-view 0 :skip-dropdown? true))
        sub-type-map (ctasks/build-subscription-attr-type-map)
        socket-subs (for [s subscriptions
                            :let [x (get sub-type-map s)]
                            :when (some #(= "sockets" %) x)] s)
        rand-sub (random-subscription socket-subs)
        rand-prod (tasks/skip-dropdown :all-subscriptions-view rand-sub)
        rand-prod-list (tasks/get-table-elements :all-available-bundled-products 0)
        pem-file (get_pem_file_for_subscription existing-prod-dir rand-prod-list)]
    create_temp_prod_dir (existing-prod-dir new-prod-dir pem-file)
    (try+
     (tasks/restart-app)
     (tasks/ui selecttab :all-available-subscriptions)
     (tasks/ui enterstring :date-entry (str new-year "-" month "-" day))
     (tasks/search :match-installed? true)
     (tasks/skip-dropdown :all-subscriptions-view rand-sub)
     (tasks/ui generatekeyevent (str
                                 (repeat-cmd 3 "<right> ")
                                 "<space> " "1 " "<enter>"))
     (tasks/subscribe rand-sub)
     (tasks/ui selecttab :my-installed-products)
     (tasks/do-to-all-rows-in :installed-view 2
                              (fn [status]
                                (verify (= "Future Subscription" status))))
     (verify (not (substring? "does not match" (tasks/ui gettextvalue :overall-status))))
     (finally
      (tasks/set-conf-file-value "productCertDir" existing-prod-dir)
      (run-command (str "rm -rf " new-prod-dir))
      (tasks/unsubscribe_all)
      (tasks/restart-app)))))

(defn ^{Test {:groups ["acceptance"
                       "blockedByBug-827173"]
              :value ["stacking-sockets"]}}
  assert_auto_attach
  "Asserts if autosubscribe is possible when client is partially subscribed"
  [_]
  (try
    (tasks/restart-app :reregister? true)
    (tasks/search :match-installed? true)
    (let [subscriptions (into [] (tasks/get-table-elements :all-subscriptions-view 0 :skip-dropdown? true))
          sub-type-map (ctasks/build-subscription-attr-type-map)
          socket-subs (for [s subscriptions
                            :let [x (get sub-type-map s)]
                            :when (some #(= "sockets" %) x)] s)
          rand-sub (random-subscription socket-subs)
          raw-data (:stdout (run-command "subscription-manager service-level --list"))
          service-level (rand-nth (drop 3 (split-lines raw-data)))]
      (tasks/skip-dropdown :all-subscriptions-view rand-sub)
      (tasks/ui generatekeyevent (str
                                  (repeat-cmd 3 "<right> ")
                                  "<space> " "1 " "<enter>"))
      (tasks/subscribe rand-sub)
      (tasks/ui selecttab :my-installed-products)
      (tasks/ui click :auto-attach)
      (sleep 8000)
      (tasks/checkforerror)
      (tasks/ui waittillwindowexist :register-dialog 80)
      (tasks/ui click :register-dialog service-level)
      (tasks/ui click :register)
      (if (tasks/ui showing? :register-dialog "Select Service Level")
        (do
          (sleep 3000)
          (tasks/ui click :register)
          (tasks/ui waittillwindownotexist :register-dialog 80)
          (tasks/restart-app)))
      (verify (= 1 (tasks/ui guiexist :main-window "System is properly subscribed*"))))
    (finally
     (tasks/unsubscribe_all))))

(defn ^{AfterGroups {:groups ["stacking"]
                     :value ["stacking-sockets"]
                     :alwaysRun true}}
  after_sockets_stacking [_]
  (tasks/write-facts "{\"cpu.cpu_socket(s)\": \"2\"}"))

(defn ^{Test {:groups ["stacking"
                       "blockedByBug-845600"]}}
  assert_quantity_displayed
  "Asserts if quantity displayed for stacking subscriptions is correct"
  [_]
  (try
    (tasks/write-facts "{\"memory.memtotal\": \"10202520\"}")
    (tasks/restart-app :reregister? true)
    (tasks/search :match-installed? true)
    (let [system-ram (Integer. 10)
          subscriptions (into [] (tasks/get-table-elements :all-subscriptions-view 0 :skip-dropdown? true))
          sub-type-map (ctasks/build-subscription-attr-type-map)
          ;; get subscriptions dependant on RAM
          only-ram (fn [i] (and (not (or (some #(= "cores" %) i) (some #(= "sockets" %) i))) (some #(= "ram" %) i)))
          ram-subs (for [s subscriptions
                            :let [x (get sub-type-map s)]
                            :when (only-ram x)] s)
          rand-sub (random-subscription ram-subs)
          subs-attrs-map (ctasks/build-subscriptions-name-val-map)
          ram-func (fn [i] (Integer. (get (get subs-attrs-map i) "ram")))
          stacking-id (get (get subs-attrs-map rand-sub) "stacking_id")
          filter-func (fn [i] (if (= stacking-id (get (get subs-attrs-map i) "stacking_id")) i))
          ;; getting subscriptions with same stacking_id
          subs-stacking-id (into [] (distinct (remove nil? (map filter-func subscriptions))))
          subs-applicable (clojure.set/intersection (into #{} ram-subs) (into #{} subs-stacking-id))
          quantity-func (fn [i] (Integer. (re-find #"\d" (tasks/ui getcellvalue :all-subscriptions-view
                                                                  (tasks/skip-dropdown :all-subscriptions-view i) 3))))
          ;; map of subscriptions having same stacking_id and the RAM that they provide
          subs-applicable-ram (into {} (map (fn [i]  {i (ram-func i) }) subs-applicable))
          ;; calculating the RAM covered by random subscription and uncovered RAM
          ram-covered (- system-ram (get subs-applicable-ram rand-sub))
          calc-func (fn [i] (int (round-up (float (/ ram-covered i)))))
          quantity-after (into [] (map calc-func (vals  subs-applicable-ram)))]
      (tasks/skip-dropdown :all-subscriptions-view rand-sub)
      (tasks/ui generatekeyevent (str
                                  (repeat-cmd 3 "<right> ")
                                  "<space> " "1 " "<enter>"))
      (tasks/subscribe rand-sub)
      (tasks/skip-dropdown :all-subscriptions-view rand-sub)
      (tasks/ui click :search)
      (sleep 4000)
      (verify (= nil (some false? (map = (into [] (map quantity-func subs-applicable)) quantity-after)))))

    (finally
     (tasks/write-facts "{\"memory.memtotal\": \"1020252\"}")
     (tasks/unsubscribe_all))))

(defn set-stacking-environment
  "This function is used to set stackig environment based on RAMs COREs or SOCKETs"
  [stacking-parameter when?]
  (cond
   (= when? "before")
   (do
     (case stacking-parameter
       ("ram")     (tasks/write-facts "{\"memory.memtotal\": \"10202520\"}")
       ("cores")   (tasks/write-facts "{\"cpu.core(s)_per_socket\": \"10\"}")
       ("sockets") (tasks/write-facts "{\"cpu.cpu_socket(s)\": \"20\"}")
       (throw (Exception. "Invalid stacking-parameter passed to function"))))
   (= when? "after")
   (do
     (case stacking-parameter
       ("ram")     (tasks/write-facts "{\"memory.memtotal\": \"1020252\"}")
       ("cores")   (tasks/write-facts "{\"cpu.core(s)_per_socket\": \"1\"}")
       ("sockets") (tasks/write-facts "{\"cpu.cpu_socket(s)\": \"2\"}")
       (throw (Exception. "Invalid stacking-parameter passed to function"))))
   :else (throw (Exception. "Invalid when? argument passed to function"))))


(defn filter-func
  "This is a helper function to provide filter function based on stacking parameter"
  [stacking-parameter]
  (case stacking-parameter
    ("ram")    (fn [i] (and (not (or (some #(= "cores" %) i) (some #(= "sockets" %) i))) (some #(= "ram" %) i)))
    ("cores")   (fn [i] (and (not (or (some #(= "sockets" %) i) (some #(= "ram" %) i))) (some #(= "cores" %) i)))
    ("sockets") (fn [i] (and (not (or (some #(= "cores" %) i) (some #(= "ram" %) i))) (some #(= "sockets" %) i)))))

(defn get-cli-facts
  "This function returns CLI fats for stacking parameters"
  [stacking-parameter]
  (let [get-value (fn [str] (Integer.
                            (trim (last (split
                                         (trim-newline(:stdout
                                                        (run-command str))) #":")))))
        ram-value (int (/ (get-value (str "subscription-manager facts --list | grep \"memory.memtotal\""))1000000))
        sockets-value (get-value (str "subscription-manager facts --list | grep \"cpu.cpu_socket\""))
        cores-value (* (get-value (str "subscription-manager facts --list | grep \"^cpu.core(s)_per_socket\""))
                       sockets-value)]
    (case stacking-parameter
      ("ram")     ram-value
      ("cores")   cores-value
      ("sockets") sockets-value
      (throw (Exception. "Invalid stacking-parameter passed to function")))))

(defn assert_product_state
  "Asserts product states when it is partially subscribed and fully subscribed
   This is test is performed for different stacking groups"
  [stacking-parameter]
  (try
    (set-stacking-environment stacking-parameter "before")
    (tasks/restart-app :reregister? true)
    (tasks/search :match-installed? true)
    (let [subscriptions (into [] (tasks/get-table-elements :all-subscriptions-view 0 :skip-dropdown? true))
          sub-type-map (ctasks/build-subscription-attr-type-map)
          func (filter-func stacking-parameter)
          subs (into [] (for [s subscriptions
                              :let [x (get sub-type-map s)]
                              :when (func x)] s))
          rand-sub (random-subscription subs)
          subs-attrs-map (ctasks/build-subscriptions-name-val-map)
          stacking-value (Integer. (get (get subs-attrs-map rand-sub) stacking-parameter))
          system-parameter (get-cli-facts stacking-parameter)
          parameter-covered (- system-parameter stacking-value)
          quantity-after (int (round-up (float (/ parameter-covered stacking-value))))
          get-quantity (fn [i] (tasks/ui getcellvalue :all-subscriptions-view
                                        (tasks/skip-dropdown :all-subscriptions-view i) 3))
          quantity (get-quantity rand-sub)
          provides-product (tasks/get-table-elements :all-available-bundled-products 0)]
      (tasks/skip-dropdown :all-subscriptions-view rand-sub)
      (tasks/ui generatekeyevent (str
                                  (repeat-cmd 3 "<right> ")
                                  "<space> " "1 " "<enter>"))
      (tasks/subscribe rand-sub)
      (tasks/ui click :search)
      (sleep 4000)
      (verify (< quantity (get-quantity rand-sub)))
      ;; verifiying if the product is partially subscribed
      (verify (= "Partially Subscribed"
                 (tasks/ui getcellvalue :installed-view
                           (tasks/skip-dropdown :installed-view
                                                (first(tasks/get-table-elements
                                                       :all-available-bundled-products 0)))2)))
      (tasks/skip-dropdown :all-subscriptions-view rand-sub)
       (tasks/ui generatekeyevent (str
                                  (repeat-cmd 3 "<right> ")
                                  "<space> " quantity-after  " <enter>"))
      ;; verifying if product is fully subscribed
      (verify (= "Subscribed"
                 (tasks/ui getcellvalue :installed-view
                           (tasks/skip-dropdown :installed-view
                                                (first(tasks/get-table-elements
                                                       :all-available-bundled-products 0)))2))))
    (finally
     (set-stacking-environment stacking-parameter "after")
     (tasks/unsubscribe_all))))

(data-driven assert_product_state {Test {:groups ["stacking"]}}
  [^{Test {:groups ["blockedByBug-845600"]}}
   (if-not (assert-skip :stacking)
     (do
       ["ram"]
       ["cores"]
       ["sockets"])
     (to-array-2d []))])

(defn ^{AfterClass {:groups ["cleanup"]
                     :alwaysRun true}}
  cleanup [_]
  (run-command (str "rm -rf " stacking-dir))
  (tasks/set-conf-file-value "productCertDir" @prod-dir-atom)
  (tasks/restart-app))

(gen-class-testng)
