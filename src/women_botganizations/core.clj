(ns women-botganizations.core
  (:require [twttr.api :as api]
            [twttr.auth :refer [map->UserCredentials]]
            [clojure.data.json :as json]
            [clojure.pprint :as pprint]))

(def creds (map->UserCredentials (read-string (slurp "creds.edn"))))

(def ignore-replies false)
(def track "ארגוני")
(def targets #{"איפה ארגוני הנשים" "למה ארגוני הנשים לא"})

(def pinuki "1295546258220879872")

(def jitter0 ["היי" "שלום" "מה נשמע"])
(def jitter1 ["אפשר" "ניתן" "רצוי"])
(def jitter2 ["לעזור" "לסייע" "לתרום"])
(def jitter3 ["בלינק הבא" "בקישור" "פה"])
(def urls ["https://bit.ly/323p3Z8"
           "https://rb.gy/ampemy"
           "https://cutt.ly/qfwjcAR"
           "https://shorturl.at/kmBK5"
           "https://www.drove.com/campaign/5cf781e4f167cf0001d1c575"])

(defn status [screen-name]
  (str
   (rand-nth jitter0)
   " "
   "@"
   screen-name
   "! "
   (rand-nth jitter1)
   " "
   (rand-nth jitter2)
   " לארגוני הנשים "
   (rand-nth jitter3)
   ": "
   (rand-nth urls)))

(defn handle-tweet [tweet]
  (try
    (when (:text tweet)
      (println "got status: " (:text tweet))
      (if (some (partial clojure.string/includes? (:text tweet)) targets)
        (if (and ignore-replies (:in_reply_to_status_id tweet))
          (println "status is a reply - ignoring")
          (do
            (println "status contains target term, replying")
            (api/statuses-update creds
                                 :params {:status (status (-> tweet :user :screen_name))
                                          :in_reply_to_user_id (-> tweet :user :id)
                                          :in_reply_to_status_id (:id tweet)
                                          ;;:media-ids [pinuki]
                                          })))
        (println "status does not contain target term")))
    (catch Exception e
      (println "error handing response")
      (pprint/pprint e))))

(def stream (atom nil))

(defn cancel-stream []
  (when @stream
    (println "cancelling track with meta" (pprint (meta @stream)))
    (reset! stream nil)))

(defn start []
  (reset! stream (api/statuses-filter creds :params {:track track}))
  (.start (Thread. (doseq [tweet @stream]
                     (try
                       (handle-tweet tweet)
                       (catch Exception e
                         (println "error getting next tweet")
                         (pprint/pprint (pr-str e))))))))
