(ns women-botganizations.core
  (:require [twttr.api :as api]
            [twttr.auth :refer [map->UserCredentials]]
            [clojure.core.async :as a]
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

(defn get-text [tweet]
  (if (not (:truncated tweet))
    (do (println "got regular tweet " (pprint tweet))
        (:text tweet))
    (do (println "got truncated tweet" (pprint/pprint tweet))
        (-> tweet :extended_tweet :full_text))))

(defn handle-tweet [tweet]
  (try
    (when-let [text (get-text tweet)]
      (println "got status: " (:id tweet))
      (println "got text: " text)
      (if (some (partial clojure.string/includes? text) targets)
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

(defn start-stream []
  (println "starting stream")
  (reset! stream (api/statuses-filter creds :params {:track track
                                                     :tweet_mode "extended"})))

(def tweets (a/chan (a/buffer 100)))

(def timeout (* 5 60 1000))

(defn take-tweet []
  (a/go-loop [tweet (take 1 @stream)]
    (a/put! tweets tweet)
    (recur (take 1 @stream))))

(defn process-tweets []
  (try
    (start-stream)
    (a/go-loop [[tweet channel] (a/alts! tweets (a/timeout timeout))]
      (when (nil? tweet)
        (println "no response for too long, restarting")
        (throw (Exception. "no tweet for too long")))
      (handle-tweet tweet)
      (recur (a/alts! tweets (a/timeout timeout))))
    (catch Exception e
      (println "error processing tweets")
      (pprint/pprint (pr-str e)))))

(defn start []
  (try
    (process-tweets)
    (catch Exception e
      (println "error getting next tweet")
      (pprint/pprint (pr-str e))
      (cancel-stream)
      (process-tweets))))
