(ns rhsm.gui.tests.repo_tests
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

(def random_row_num (atom nil)) ;; Used to dynamically select a random row number
(def list_row (atom []))       ;; Used to hold probable row numbers
(def no_repos_message "No repositories are available without an attached subscription.")

(defn ^{BeforeClass {:groups ["setup"]}}
  setup [_]
  (try
    (if (= "RHEL7" (get-release)) (base/startup nil))
    (tasks/restart-app)
    (tasks/unregister)
    (catch Exception e
      (reset! (skip-groups :repo) true)
      (throw e))))

(defn ^{Test {:groups ["repo"]}}
  check_repo_system_menu
  "This tests for repository option in the system menu"
  [_]
  (if (not (tasks/ui showing? :register-system))
    (tasks/unregister))
  (tasks/ui click :repositories)
  (tasks/ui waittillguiexist :repositories-dialog)
  (verify (bash-bool (tasks/ui guiexist :repositories-dialog))))

(defn ^{Test {:groups ["repo"]}}
  check_repo_message_unsubscribed
  "This tests for default static message in repository dialog when unsubscribed"
  [_]
  (try
    (tasks/restart-app :reregister? true)
    (tasks/ui click :repositories)
    (tasks/ui waittillguiexist :repositories-dialog)
    (verify (bool (tasks/ui guiexist :repositories-dialog)))
    (verify (= no_repos_message (tasks/ui gettextvalue :repo-message)))
    (finally
     (tasks/ui click :close-repo-dialog))))

(defn ^{Test {:groups ["repo"]}}
  check_repo_table_populated
  "This tests if repo-table is populated when subscribed"
  [_]
  (try
    (tasks/restart-app :reregister? true)
    (tasks/subscribe_all)
    (tasks/ui click :repositories)
    (tasks/ui waittillguiexist :repositories-dialog)
    (verify (bool (tasks/ui guiexist :repositories-dialog)))
    (verify (not (= 0 (tasks/ui getrowcount :repo-table))))
    (finally
     (tasks/ui click :close-repo-dialog)
     (tasks/unsubscribe_all))))

(defn ^{Test {:groups ["repo"]}}
  check_repo_remove_override_button
  "This tests if repo-override button is enabled when a row is checked"
  [_]
  (try
    (tasks/restart-app :reregister? true)
    (tasks/subscribe_all)
    (tasks/ui click :repositories)
    (tasks/ui waittillguiexist :repositories-dialog)
    (verify (bool (tasks/ui guiexist :repositories-dialog)))
    (verify (bash-bool (tasks/ui hasstate :repo-remove-override "enabled")))
    (if (= 0 (tasks/ui getrowcount :repo-table))
      (throw (Exception. "Repositories table is not populated"))
      (do
        (let
            [row-count (tasks/ui getrowcount :repo-table)
             list-row (into [] (range row-count))
             random-row-num (nth list-row (rand (count list-row)))]
          (tasks/ui selectrowindex :repo-table random-row-num)
          (tasks/ui checkrow :repo-table random-row-num)
          (verify (bool (and (tasks/ui hasstate :repo-remove-override "enabled")
                             (tasks/ui hasstate :repo-remove-override "sensitive"))))
          (tasks/ui uncheckrow :repo-table random-row-num)
          (verify (bash-bool (and (tasks/ui hasstate :repo-remove-override "enabled")
                                  (tasks/ui hasstate :repo-remove-override "sensitive")))))))
    (finally
     (tasks/ui click :close-repo-dialog)
     (tasks/unsubscribe_all))))

(defn ^{Test {:groups ["repo"]}}
  check_repo_gpgcheck_button
  "This tests gpg-check edit and remove button"
  [_]
  (try
    (tasks/restart-app :reregister? true)
    (tasks/subscribe_all)
    (tasks/ui click :repositories)
    (tasks/ui waittillguiexist :repositories-dialog)
    (let
        [row-count (tasks/ui getrowcount :repo-table)]
      (reset! list_row (into [] (range row-count)))
      (reset! random_row_num (nth @list_row (rand (count @list_row))))
      (tasks/ui selectrowindex :repo-table @random_row_num)
      (while (and (bash-bool (tasks/ui hasstate :gpg-check-edit "visible"))
                  (< 1 (count @list_row)))
        (reset! list_row (remove #(= @random_row_num %) @list_row))
        (reset! random_row_num (nth @list_row (int (rand (count @list_row)))))
        (tasks/ui selectrowindex :repo-table @random_row_num))
      (sleep 1000)
      (verify (bool (tasks/ui hasstate :gpg-check-edit "visible")))
      (verify (bash-bool (tasks/ui hasstate :gpg-check-remove "visible")))
      (tasks/ui click :gpg-check-edit)
      (sleep 2000)
      (verify (bool (tasks/ui hasstate :gpg-check-remove "visible")))
      (verify (bash-bool (tasks/ui hasstate :gpg-check-edit "visible"))))
    (finally
     (tasks/ui click :gpg-check-remove)
     (tasks/ui waittillwindowexist :question-dialog 30)
     (tasks/ui click :yes)
     (tasks/checkforerror)
     (tasks/ui click :close-repo-dialog)
     (tasks/unsubscribe_all))))

(defn ^{BeforeGroups {:groups ["repo"]
                      :value ["assert_remove_all_overides"]}}
  before_enable_repo_remove_all_overrides
  "Modofies all repos by clicking edit gpg-check"
  [_]
  (tasks/restart-app :reregister? true)
  (tasks/subscribe_all)
  (tasks/ui click :repositories)
  (tasks/ui waittillguiexist :repositories-dialog)
  (tasks/do-to-all-rows-in :repo-table 1
                           (fn [repo]
                             (tasks/ui selectrow :repo-table repo)
                             (if (bool (tasks/ui hasstate :gpg-check-edit "visible"))
                               (tasks/ui click :gpg-check-edit)))))

(defn ^{Test {:groups ["repo"]
              :value ["assert_remove_all_overides"]}}
  enable_repo_remove_all_overrides
  "Enable all repos and click remove all override and check state"
  [_]
  (tasks/do-to-all-rows-in :repo-table 1
                           (fn [repo]
                             (tasks/ui selectrow :repo-table repo)
                             (tasks/ui click :repo-remove-override)
                             (tasks/ui waittillwindowexist :question-dialog 30)
                             (verify (substring?
                                      repo (tasks/ui gettextvalue
                                                     :question-dialog "Are you sure*")))
                             (tasks/ui click :yes)
                             (tasks/checkforerror))))

(defn ^{AfterGroups {:groups ["repo"]
                     :value ["assert_remove_all_overides"]
                     :alwaysRun true}}
  after_enable_repo_remove_all_overrides
  [_]
  (tasks/ui click :close-repo-dialog)
  (tasks/unsubscribe_all))

(defn ^{BeforeGroups {:groups ["repo"]
                      :value ["assert_overide_persistance"]}}
  before_verify_override_persistance
  "Modofies all repos by clicking edit gpg-check"
  [_]
  (tasks/restart-app :reregister? true)
  (tasks/subscribe_all)
  (tasks/ui click :repositories)
  (tasks/ui waittillguiexist :repositories-dialog)
  (tasks/do-to-all-rows-in :repo-table 1
                           (fn [repo]
                             (tasks/ui selectrow :repo-table repo)
                             (if (bool (tasks/ui hasstate :gpg-check-edit "visible"))
                               (tasks/ui click :gpg-check-edit))))
  (tasks/ui click :close-repo-dialog)
  (tasks/unsubscribe_all))

(defn ^{Test {:groups ["repo"]
              :value ["assert_overide_persistance"]}}
  verify_override_persistance
  "Enable all repos and click remove all override and check state"
  [_]
  (tasks/subscribe_all)
  (tasks/ui click :repositories)
  (tasks/ui waittillguiexist :repositories-dialog)
  (tasks/do-to-all-rows-in :repo-table 1
                           (fn [repo]
                             (tasks/ui selectrow :repo-table repo)
                             (verify (bool (tasks/ui hasstate :gpg-check-remove "visible")))
                             (verify (bash-bool (tasks/ui hasstate :gpg-check-edit "visible"))))))

(defn ^{AfterGroups {:groups ["repo"]
                     :value ["assert_overide_persistance"]
                     :alwaysRun true}}
  after_verify_override_persistance
  [_]
  (tasks/do-to-all-rows-in :repo-table 1
                           (fn [repo]
                             (tasks/ui selectrow :repo-table repo)
                             (tasks/ui click :gpg-check-remove)
                             (tasks/ui waittillwindowexist :question-dialog 30)
                             (tasks/ui click :yes)
                             (tasks/checkforerror)))
  (tasks/ui click :close-repo-dialog)
  (tasks/unsubscribe_all))

(defn ^{Test {:groups ["repo"]
              :value ["assert_repo_dialog_fields"]
              :dataProvider "repolist"}}
  check_repo_name_url
  "Checks if name and URL are populated for all repositories"
  [_ repo]
  (tasks/ui selectrow :repo-table repo)
  (verify (not (blank? (tasks/ui gettextvalue :base-url))))
  (verify (not (blank? (tasks/ui gettextvalue :repo-name))))
  (verify (not (blank? (tasks/ui gettextvalue :gpg-check-text)))))

(defn ^{AfterGroups {:groups ["repo"]
                     :value ["assert_repo_dialog_fields"]
                     :alwaysRun true}}
  after_check_repo_name_url
  [_]
  (tasks/ui click :close-repo-dialog)
  (tasks/unsubscribe_all))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DATA PROVIDERS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^{DataProvider {:name "repolist"}}
  subscribed_repos [_ & {:keys [debug]
                       :or {debug false}}]
  (if-not (assert-skip :repo)
    (do
      (tasks/restart-app :reregister? true)
      (tasks/subscribe_all)
      (tasks/ui click :repositories)
      (tasks/ui waittillguiexist :repositories-dialog)
      (let [repos (into [] (map vector (tasks/get-table-elements
                                       :repo-table
                                       1)))]
        (if-not debug
          (to-array-2d repos)
          repos)))
    (to-array-2d [])))
  
(gen-class-testng)
