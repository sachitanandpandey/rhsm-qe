(ns rhsm.gui.tasks.tasks
  (:use [rhsm.gui.tasks.test-config :only (config
                                           clientcmd
                                           cli-tasks
                                           auth-proxyrunner
                                           noauth-proxyrunner)]
        [slingshot.slingshot :only [throw+ try+]]
        [com.redhat.qe.verify :only (verify)]
        [clojure.string :only (split
                               split-lines
                               trim
                               blank?)]
        rhsm.gui.tasks.tools
        matchure
        gnome.ldtp)
  (:require [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [rhsm.gui.tasks.candlepin-tasks :as ctasks]
            rhsm.gui.tasks.ui) ;;need to load ui even if we don't refer to it because of the extend-protocol in there.
  (:import [com.redhat.qe.tools RemoteFileTasks]
           [rhsm.cli.tasks CandlepinTasks]
           [rhsm.base SubscriptionManagerBaseTestScript]))


(def ui gnome.ldtp/action) ;;alias action in ldtp to ui here
(def rhsm-gui-pid (atom nil))

;; A mapping of RHSM error messages to regexs that will match that error.
(def known-errors {:invalid-credentials #"Invalid Credentials|Invalid username or password.*"
                   :no-username #"You must enter a login"
                   :no-password #"You must enter a password"
                   :wrong-consumer-type #"Consumers of this type are not allowed"
                   :network-error #"Unable to reach the server at"
                   :error-updating #"Error updating system data*"
                   :date-error #"Invalid date format. Please re-enter a valid date*"
                   :invalid-cert #"The following files are not valid certificates and were not imported"
                   :cert-does-not-exist #"The following certificate files did not exist"
                   :no-sla-available #"No service level will cover all installed products"
                   })

(defn matching-error
  "Returns a keyword of known error, if the message matches any of them."
  [message]
  (let [matches-message? (fn [key] (let [re (known-errors key)]
                                    (if (re-find re message) key false)))]
    (or (some matches-message? (keys known-errors))
        :sm-error)))

(defn connect
  ([url] (set-url url))
  ([] (connect (@config :ldtp-url))))

(defn settext
  "RHEL5 SUCKS"
  [location text]
  (try
    (ui settextvalue location text)
    (catch Exception e
      (if (substring? "not implemented" (.getMessage e))
        (do (sleep 2000)
            (if (= text "")(ui generatekeyevent "<bksp>")
                (ui generatekeyevent text)))
        (throw e)))))

(defn start-app
  "starts the subscription-manager-gui
  @path: lauch the application at [path]"
  ([]
     (start-app (@config :binary-path) :main-window (get-default-locale)))
  ([path]
     (start-app path nil (get-default-locale)))
  ([path window lang]
     (let [ldtp-pid (ui launchapp path [] 10 1 lang)
           pid (trim (:stdout (run-command (str "pgrep -f " path))))]
       (reset! rhsm-gui-pid pid)
       (log/info (str path " pid: " @rhsm-gui-pid))
       (log/info (str "ldtp pid for " path ": " ldtp-pid )))
     (when window
       (do
         (ui waittillwindowexist window 30)
         (try (assert (bool (ui guiexist window))
                      "subsciption-manager-gui did not launch!")
              (catch AssertionError e
                (reset! rhsm-gui-pid nil)
                (throw e)))
         (sleep 500)
         (ui maximizewindow window)))))

(defn kill-app
  "Kills the subscription-manager-gui"
  ([force?]
      (ui closewindow :main-window)
      (let [result? (bool (ui waittillwindownotexist :main-window 10))]
        (if (and (not result?) force?)
          (do
            (run-command "killall -9 subscription-manager-gui")
            (ui waittillwindownotexist :main-window 30))))
      (sleep 1000))
  ([]
     (if (= "RHEL7" (get-release))
       (kill-app false) ;; force quitting on rhel7 destroys ldtp
       (kill-app true))))

(defn start-firstboot
  "Convenience function that calls start-app with the firstboot path."
  []
  (if (and (= "true" (trim (:stdout (run-command "test -f  /etc/sysconfig/firstboot && echo \"true\""))))
           (= "NO" (.getConfFileParameter @cli-tasks "/etc/sysconfig/firstboot" "RUN_FIRSTBOOT")))
    (.updateConfFileParameter @cli-tasks  "/etc/sysconfig/firstboot" "RUN_FIRSTBOOT" "YES"))
  (let [path (@config :firstboot-binary-path)]
    (ui launchapp path [] 10)
    (ui waittillwindowexist :firstboot-window 30)))

(defn get-error-msg
  "Retrieves the error string from the RHSM error dialog."
  []
  (.trim (ui getobjectproperty :error-msg "label")))

(defn clear-error-dialog
  "Clears an error dialog by clicking OK."
  []
  (ui click :ok-error))

(defn checkforerror
  "Checks for the error dialog for 3 seconds and logs the error message.
  Allows for recovery of the error message.
  @wait: specify the time to wait for the error dialog."
  ([wait]
     (if (bool (ui waittillwindowexist :error-dialog wait))
       (let [message (get-error-msg)
             type (matching-error message)]
         (clear-error-dialog)
         (throw+ {:type type
                  :msg message
                  :log-warning (fn []
                                 (log/warn
                                  (format "Got error '%s', message was: '%s'"
                                          (name type) message)))}))))
  ([] (checkforerror 3)))

(defn set-conf-file-value
  "Edits /etc/rhsm/rhsm.conf to set values within it.
  field: the field to change.
  value: the value to set the field to."
  [field value]
  (.updateConfFileParameter @cli-tasks (.rhsmConfFile @cli-tasks) field value))

(defn conf-file-value
  "Grabs the value of a field in /etc/rhsm/rhsm.conf."
  [k]
  (.getConfFileParameter @cli-tasks (.rhsmConfFile @cli-tasks) k))

(defn setchecked [needs-check?] (if needs-check? check uncheck))

(defn unregister
  "Unregisters subscripton manager by clicking the 'unregister' button."
  []
  (if (or
       ;rhel6
       (ui showing? :register-system)
       ;rhel5 - add 'visible?' helper in gnome.ldtp later
       (bool (ui hasstate :register-system "VISIBLE")))
    (throw+ {:type :not-registered
            :msg "Tried to unregister when already unregistered."}))
  (ui click :unregister-system)
  (ui waittillwindowexist :question-dialog 30)
  (ui click :yes)
  (checkforerror))

(defn register
  "Registers subscription manager by clicking the 'Register System' button in the gui."
  [username password & {:keys [system-name-input
                               skip-autosubscribe
                               owner
                               sla
                               auto-select-sla
                               server]
                        :or {system-name-input nil
                             skip-autosubscribe true
                             owner nil
                             sla nil
                             auto-select-sla true
                             server nil}}]
  (ui selecttab :my-installed-products)
  (if-not (ui showing? :register-system)
    (throw+ {:type :already-registered
             :username username
             :password password
             :system-name-input system-name-input
             :skip-autosubscribe skip-autosubscribe
             :owner owner
             :sla sla
             :auto-select-sla auto-select-sla
             :server server
             :unregister-first (fn []
                                 (unregister)
                                 (register username
                                           password
                                           :system-name-input system-name-input
                                           :skip-autosubscribe skip-autosubscribe
                                           :owner owner
                                           :sla sla
                                           :auto-select-sla auto-select-sla
                                           :server server))}))
  (ui click :register-system)
  (ui waittillguiexist :register-dialog)
  ;server selection screen
  (when server (ui settextvalue :register-server server))
  (ui click :register)
  (checkforerror)
  ;login screen
  (ui settextvalue :redhat-login username)
  (ui settextvalue :password password)
  (when system-name-input
    (ui settextvalue :system-name system-name-input))
  (ui (setchecked skip-autosubscribe) :skip-autobind)
  (try+
   (ui click :register)
   (checkforerror 10)
   (if (bool (ui guiexist :register-dialog))
     (do
       ;; handle owner selection
       (if (bool (ui waittillshowing :owner-view 30))
         (do
           (when owner (do
                         (if-not (ui rowexist? :owner-view owner)
                           (throw+ {:type :owner-not-available
                                    :name owner
                                    :msg (str "Not found in 'Owner Selection':" owner)})
                           (ui selectrow :owner-view owner))))
           (ui click :register)
           (checkforerror 10)))
       ;;(ui waittillnotshowing :registering 1800)  ;; 30 minutes
       ))
   (if ;(bool (ui guiexist :subscribe-system-dialog))
       (bool (ui guiexist :register-dialog))
     (do
       (if auto-select-sla
         (do
           (if (bool (ui guiexist :register-dialog "Confirm Subscriptions")) ;; sla selection is presented
             (do (when sla (ui click :register-dialog sla))
                 (ui click :register)))
           (ui click :register))
         ;; else leave sla dialog open
         (when sla (ui click :register-dialog sla)))))
   (checkforerror)
   (catch Object e
     ;; this was rewritten, though hackey, this is prefered over adding a hard wait.
     (let [recovery (fn [h]
                      (if (= :no-sla-available (:type h))
                        (throw+ h)
                        (throw+ (into e {:cancel (fn [] (ui click :register-cancel))}))))]
       (if-not (:msg e)
         (let [error (checkforerror)]
           (if (nil? error)
             (throw+ e)
             (recovery error)))
         (recovery e)))))
  (sleep 10000))

(defn fbshowing?
  "Utility to see if a GUI object in firstboot on a RHEL5 system is showing."
  ([item]
     ;; since all items exist at all times in firstboot,
     ;;  we must poll the states and see if 'SHOWING' is among them
     ;; "SHOWING" == 24  on RHEL5
     (or (= 24 (some #{24} (seq (ui getallstates item))))
         (ui showing? item)))
  ([window_name component_name]
     (or
      (if (some #(re-find (re-pattern (str ".*" component_name ".*")) %)
                (seq (ui getobjectlist window_name)))
        (= 24 (some #{24} (seq (ui getallstates window_name component_name))))
        false)
      (ui showing? window_name component_name))))

(defn firstboot-register
  "Subscribes subscription-manager from within firstboot."
  [username password & {:keys [server
                               server-default?
                               activation-key
                               activation?
                               system-name-input
                               skip-autosubscribe?
                               org
                               back-button?]
                        :or {server nil
                             server-default? false
                             activation-key nil
                             activation? false
                             system-name-input nil
                             skip-autosubscribe? true
                             org nil
                             back-button? false}}]
  (assert  (or (fbshowing? :firstboot-server-entry)
               (bool (ui guiexist :firstboot-window "Subscription Management Registration"))))
  (when server (ui settextvalue :firstboot-server-entry server))
  (if server-default? (ui click :firstboot-server-default))
  (ui (setchecked (or activation-key activation?)) :firstboot-activation-checkbox)
  (ui click :firstboot-forward)
  (if-not (or activation-key activation?)
    (do
      (ui settextvalue :firstboot-user username)
      (ui settextvalue :firstboot-pass password)
      (when system-name-input
        (ui settextvalue :firstboot-system-name system-name-input))
      (ui (setchecked skip-autosubscribe?) :firstboot-autosubscribe)
      (ui click :firstboot-forward)
      (checkforerror)
      (if (ui showing? :firstboot-window "org_selection_label")
        (do
          (if org (ui selectrow :firstboot-owner-table org))
          (ui click :firstboot-forward)))
      ;;TODO: write autosubscribe selection methods
       (if back-button?
        (do
        (verify (not (bool (ui hasstate :firstboot-back "sensitive"))))
        (verify (not (bool (ui hasstate :firstboot-forward "sensitive")))))))
    ;;TODO: write activation key path
    )
  (checkforerror))

(defn wait-for-progress-bar
  "Waits for a progress bar to finish."
  []
  (ui waittillwindowexist :search-dialog 1)
  (ui waittillwindownotexist :search-dialog 30)
  (comment ;; removed in BZ 818238
    (ui waittillwindowexist :progress-dialog 1)
    (ui waittillwindownotexist :progress-dialog 30))
  (checkforerror))

;; TODO: write this with better airities
(defn search
  "Performs a subscription search within subscription-manager-gui."
  [& {:keys [match-system?, do-not-overlap?, match-installed?, contain-text, active-on]
     :or {match-system? true
          do-not-overlap? true
          match-installed? false
          contain-text nil
          active-on nil}}]
  (ui selecttab :all-available-subscriptions)
  (ui click :filters)
  (ui waittillwindowexist :filter-dialog 5)
  (ui (setchecked match-system?) :match-system)
  (ui (setchecked do-not-overlap?) :do-not-overlap)
  (ui (setchecked match-installed?) :match-installed)
  (if contain-text
    (ui settextvalue :contain-the-text contain-text)
    (ui settextvalue :contain-the-text ""))
  (ui click :close-filters)
  (if active-on ;BZ#675777
    (ui settextvalue :date-entry active-on))
  (ui click :search)
  (wait-for-progress-bar))

(defn is-item?
  "Determines if an item in a table is a dropdown or an item."
  [table rownum]
  (try (let [value (ui getcellvalue table rownum 2)]
         (if (= 0 value)
           (try (let [value (ui getcellvalue table rownum 3)]
                  (if (= 0 value)
                    false
                    true))
                (catch Exception e true))
           true))
       (catch Exception e false)))

(defn skip-dropdown
  "Skips dropdown items in a table."
  [table item]
  (if-not (ui rowexist? table item)
    (throw+ {:type :item-not-available
             :name item
             :msg (str "Not found in " table ": " item)}))
  (let [row (ui gettablerowindex table item)]
    (if (is-item? table row)
      (do
        (ui selectrow table item)
        row)
      (do
        (if (and (is-item? table (+ 1 row))
                 (= item (ui getcellvalue table (+ 1 row) 0)))
          (do (ui selectrowindex table (+ 1 row))
              (+ 1 row))
          (throw+ {:type :invalid-item
                   :name item
                   :msg (str "Invalid item:" item)}))))))

(defn open-contract-selection
  "Opens the contract selection dialog for a given subscription."
  [s]
  (ui selecttab :all-available-subscriptions)
  (if (= "0 *" (ui getcellvalue :all-subscriptions-view
                   (skip-dropdown :all-subscriptions-view s) 3))
    (do
      (ui generatekeyevent (str
                            (repeat-cmd 3 "<right> ")
                            "<space> " "<up> " "<enter>"))))
  (ui click :attach)
  (checkforerror)
  (if-not (bool (ui waittillwindowexist :contract-selection-dialog 5))
    (throw+ {:type :contract-selection-not-available
             :name s
             :msg (str s " does not have multiple contracts.")})))

(defn subscribe
  "Subscribes to a given subscription, s."
  ([subscription contract quantity] ;;subscribe to a given contract, subscription, and quantity
     (open-contract-selection subscription)
     (ui selectrow :contract-selection-table contract)
     (let [pool (ctasks/get-pool-id (@config :username)
                                    (@config :password)
                                    (@config :owner-key)
                                    subscription
                                    contract)
           multiplier (ctasks/get-instance-multiplier (@config :username)
                                                      (@config :password)
                                                      pool
                                                      :string? false)
           line (ui gettablerowindex :contract-selection-table contract)
           usedmax (ui getcellvalue :contract-selection-table line 2)
           used (first (split usedmax #" / "))
           max (last (split usedmax #" / "))
           enter-quantity (fn [num]
                            (ui generatekeyevent
                                (str (repeat-cmd 5 "<right> ")
                                     "<space>"
                                     (when num (str " "
                                                    (repeat-cmd (.length max) "<backspace> ")
                                                    (- num (mod num multiplier))
                                                    " <enter>")))))]
       (when quantity (do (enter-quantity quantity)
                          (sleep 500)))
       (ui click :attach-contract-selection)
       (checkforerror)
       (wait-for-progress-bar)))
  ([s c] ;;use the default quantity
     (subscribe s c nil))
  ([s] ;;simple subscribe that picks the first contract and default quantity
     (ui selecttab :all-available-subscriptions)
     (if (= "0 *" (ui getcellvalue :all-subscriptions-view
                      (skip-dropdown :all-subscriptions-view s) 3))
       (do
         (ui generatekeyevent (str
                               (repeat-cmd 3 "<right> ")
                               "<space> " "<up> " "<enter>"))))
     (ui click :attach)
     (checkforerror)
     (ui waittillwindowexist :contract-selection-dialog 5)
     (if (bool (ui guiexist :contract-selection-dialog))
       (do (ui selectrowindex :contract-selection-table 0)
           (ui click :attach-contract-selection)))
     (checkforerror)
     (wait-for-progress-bar)))

(defn unsubscribe
  "Unsubscribes from a given subscription, s"
  [s]
  (ui selecttab :my-subscriptions)
  (sleep 5000)
  (if-not (ui rowexist? :my-subscriptions-view s)
    (throw+ {:type :not-subscribed
             :name s
             :msg (str "Not found in 'My Subscriptions': " s)}))
  (ui selectrow :my-subscriptions-view s)
  (ui click :remove)
  (ui waittillwindowexist :question-dialog 60)
  (ui click :yes)
  (checkforerror))

(defn subscribe_all
  "Subscribes to everything available"
  []
  (comment ;; No longer used just here for reference
    (allsearch)
    (tasks/do-to-all-rows-in
     :all-subscriptions-view 1
     (fn [subscription]
       (try+ (tasks/subscribe subscription)
             (catch [:type :item-not-available] _)
             (catch [:type :wrong-consumer-type]
                 {:keys [log-warning]} (log-warning))))
     :skip-dropdowns? true))
  (let [all-pools (map :id (ctasks/list-available true))
        all-prods (map :productName (ctasks/list-available true))
        mapify (fn [key val] (zipmap key val))
        syntax (fn [item] (str "--pool=" item " "))
        command (str "subscription-manager attach "
                     (clojure.string/join (map syntax (vals (mapify all-prods all-pools)))))]
        (run-command command)))

(defn unsubscribe_all
  "Unsubscribes from everything available"
  []
  (comment  ;; No longer used just here for reference
    (tasks/ui selecttab :my-subscriptions)
    (tasks/do-to-all-rows-in
     :my-subscriptions-view 0
     (fn [subscription]
       (try+
        (tasks/unsubscribe subscription)
        (verify (= (tasks/ui rowexist? :my-subscriptions-view subscription) false))
        (catch [:type :not-subscribed] _)))
     :skip-dropdowns? true))
  (:stdout (run-command "subscription-manager unsubscribe --all")))

(defn enableproxy
  "Configures a proxy that uses authentication through subscription-manager-gui."
  [server & {:keys [port user pass auth? close? firstboot?]
                  :or {port nil
                       user nil
                       pass nil
                       auth? false
                       close? true
                       firstboot? false}}]
  (assert (every? #(is-boolean? %) (vector auth? close? firstboot?)))
  (if-not firstboot?
    (do (ui click :configure-proxy)
        (ui waittillwindowexist :proxy-config-dialog 60)
           (ui check :proxy-checkbox)
           (try
             (ui settextvalue :proxy-location (str server (when port (str ":" port))))
             (catch Exception e
               (if (substring? "not implemented" (.getMessage e))
                 (do (sleep 2000)
                     (ui generatekeyevent (str server (when port (str ":" port)))))
                 (throw e))))
           (if (or auth? user pass)
             (do (ui check :authentication-checkbox)
                 (when user (ui settextvalue :username-text user))
                 (when pass (ui settextvalue :password-text pass)))
             (ui uncheck :authentication-checkbox))
           (if close? (ui click :close-proxy))
           (checkforerror))
    ;;firstboot
    (do (assert (ui showing? :firstboot-window "Choose Service"))
        (ui click :firstboot-proxy-config)
        (ui waittillwindowexist :firstboot-proxy-dialog 60)
        (ui check :firstboot-proxy-checkbox)
        (settext :firstboot-proxy-location (str server (when port (str ":" port))))
        (if (or auth? user pass)
          (do (ui check :firstboot-auth-checkbox)
              (when user (settext :firstboot-proxy-user user))
              (when pass (settext :firstboot-proxy-pass pass)))
          (ui uncheck :firstboot-auth-checkbox))
        (if close? (ui click :firstboot-proxy-close)))))

(defn disableproxy
  "Disables any proxy settings through subscription-manager-gui."
  ([firstboot]
     (assert (is-boolean? firstboot))
     (if firstboot
       (do (ui click :firstboot-proxy-config)
           (ui waittillwindowexist :firstboot-proxy-dialog 60)
           (ui check :firstboot-proxy-checkbox)
           (settext :firstboot-proxy-location "")
           (ui uncheck :firstboot-proxy-checkbox)
           (ui check :firstboot-auth-checkbox)
           (settext :firstboot-proxy-user "")
           (settext :firstboot-proxy-pass "")
           (ui uncheck :firstboot-auth-checkbox)
           (ui click :firstboot-proxy-close)
           (checkforerror))
       (do (ui selecttab :my-installed-products)
           (ui click :configure-proxy)
           (ui waittillwindowexist :proxy-config-dialog 60)
           (ui check :proxy-checkbox)
           (ui uncheck :authentication-checkbox)
           (ui uncheck :proxy-checkbox)
           (ui click :close-proxy)
           (checkforerror))))
  ([] (disableproxy false)))

(defn warn-count
  "Grabs the number of products that do not have a valid subscription tied to them
  as reported by the GUI."
  []
  (let [status (ui gettextvalue :overall-status)]
    (if (or (substring? "installed products do not have" status)
            (substring? "installed product does not have" status))
      (Integer. (first (split status #" ")))
      0)))

(defn compliance?
  "Returns true if the GUI reports that all products have a valid subscription."
  []
  (let [status (ui gettextvalue :overall-status)]
    (or  (substring? "System is properly subscribed through" status)
         (substring? "No installed products detected." status))))

(defn get-table-elements
  "Returns a vector containing all elements in a given table and column."
  [view col & {:keys [skip-dropdowns?]
               :or {skip-dropdowns? false}}]
  (if-not skip-dropdowns?
    (do
      (for [row (range (action getrowcount view))]
        (ui getcellvalue view row col)))
    (do
      (let [allitems (get-table-elements view 0)
            item-filter (fn [item] (is-item? view item))
            rownums (filter item-filter (range (ui getrowcount view)))
            items (map (fn [rowid]
                         (ui getcellvalue view rowid 0))
                       rownums)]
        items))))

(defn do-to-all-rows-in
  "Perferms a given function on all elements in a given table and column."
  [view col f & {:keys [skip-dropdowns?]
                 :or {skip-dropdowns? false}}]
  (let [item-list (get-table-elements view col :skip-dropdowns? skip-dropdowns?)]
    (doseq [item item-list]
      (f item))))

(defn verify-conf-proxies
  "Utility function to help verify that the proxy values in
  /etc/rhsm/rhsm.conf match what is expected."
  [hostname port user password]
  (let [config-file-hostname  (conf-file-value "proxy_hostname")
        config-file-port      (conf-file-value "proxy_port")
        config-file-user      (conf-file-value "proxy_user")
        config-file-password  (conf-file-value "proxy_password")]
    (verify (= config-file-hostname hostname))
    (verify (= config-file-port port))
    (verify (= config-file-user user))
    (verify (= config-file-password password))))

(defn register-with-creds
  "Registers user with credentials found in automation.properties"
  [& {:keys [re-register?]
    :or {re-register? true}}]
  (let [ownername (ctasks/get-owner-display-name (@config :username)
                                                 (@config :password)
                                                 (@config :owner-key))
        server (ctasks/server-path)]
    (if re-register?
      ;re-register with handlers
      (try+
       (register (@config :username)
                 (@config :password)
                 :owner ownername
                 :server server)
       (catch
           [:type :already-registered]
           {:keys [unregister-first]}
         (unregister-first)))
      ;else just attempt a register
      (register (@config :username)
                (@config :password)
                :owner ownername
                :server server))))

(defn restart-app
  "Restarts subscription-manager-gui"
  [& {:keys [unregister?
             reregister?]
      :or {unregister? false
           reregister? false}}]
  (kill-app)
  (if (or unregister? reregister?)
    (run-command "subscription-manager unregister"))
  (start-app)
  (if reregister?
    (register-with-creds)))

(defn get-all-facts
  "Creates and returns a map of all system facts as read in via the rhsm-gui"
  []
  (ui click :view-system-facts)
  (ui waittillguiexist :facts-view)
  (sleep 5000)
  (let [groups (get-table-elements :facts-view 0)
        getcell (fn [row col]
                   (ui getcellvalue :facts-view row col))
        is-data? (fn [rownum]
                   (try (let [value (getcell rownum 1)
                              field (getcell rownum 0)]
                          (if-not (= value 0)
                            true
                            (if-not (= nil (some #{\.} (seq field)))
                              true
                              false)))
                        (catch Exception e false)))
        _ (doseq [item groups] (do (ui selectrow :facts-view item)
                                   (sleep 500)
                                   (ui doubleclickrow :facts-view item)
                                   (sleep 500)))
        rownums (filter is-data? (range (ui getrowcount :facts-view)))
        facts (into {} (map (fn [rowid]
                              [(getcell rowid 0) (getcell rowid 1)])
                               rownums))]
    (ui click :close-facts)
    facts))

(defn write-facts
  "Writes overriding facts file into /etc/rhsm/facts/
    @facts      : facts to be written. Either a json formatted string or
                   a clojure datastructure to be converted to a json string
    @filename   : file name of the facts override file
    @path       : path of the filename
    @overwrite? : boolean to overwrite the file
    @update?    : boolean to update facts with candlepin"
  [facts & {:keys [filename path overwrite? update?]
            :or {filename "override.facts"
                 path "/etc/rhsm/facts/"
                 overwrite? true
                 update? true}}]
  (let [redirect (if overwrite? ">" ">>")
        cur-contents (if (and (not overwrite?)
                              (bash-bool (:exitcode (run-command
                                                     (str "test -e " path filename)))))
                       (let [s (trim (:stdout (run-command (str "cat " path filename))))]
                         (if (blank? s) {} (json/read-json s)))
                       nil)
        newfacts (if (string? facts) (json/read-json facts) facts)
        contents (if cur-contents
                   (json/json-str (into cur-contents newfacts))
                   (json/json-str newfacts))
        command (str "echo '" contents "' " redirect " " path filename)]
    (run-command command)
    (if update?
      (run-command "subscription-manager facts --update"))))

(defn get-stackable-pem-files
  "Builds a map of product names and .pem files"
  []
  (let
      [prod-dir (conf-file-value "productCertDir")
       stacking-map (ctasks/build-stacking-id-map :all? true)
       raw-pem-files (:stdout (run-command
                               (str "cd " prod-dir "; for x in `ls`; do rct cat-cert $x | egrep \"Path\" | cut -d: -f 2; done")))
       pem-file-list (into [] (map clojure.string/trim (clojure.string/split-lines raw-pem-files)))
       raw-prods (:stdout (run-command
                           (str "cd " prod-dir "; for x in `ls`; do rct cat-cert $x | egrep \"Name\" | cut -d: -f 2; done")))
       prod-list (into [] (map clojure.string/trim (clojure.string/split-lines raw-prods)))
       prod-pem-file-map (zipmap prod-list pem-file-list)
       stacking-id? (fn [i] (not-nil? (some #{"stacking_id"} (map :name (:attrs i)))))
       stackable-prods (flatten (distinct (map (fn [i] (:pp i))
                                              (filter stacking-id? stacking-map))))
       stackable-pems (map (fn [i] (get prod-pem-file-map i)) stackable-prods)]
    stackable-pems))
