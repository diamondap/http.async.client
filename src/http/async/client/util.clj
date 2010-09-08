; Copyright 2010 Hubert Iwaniuk
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;   http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns http.async.client.util
  "Asynchronous HTTP Client - Clojure - Utils"
  {:author "Hubert Iwaniuk"}
  (:refer-clojure :exclude [promise])
  (:require [http.async.client.request :as r]))

(defn promise
  "Alpha - subject to change.
  Returns a promise object that can be read with deref/@, and set,
  once only, with deliver. Calls to deref/@ prior to delivery will
  block. All subsequent derefs will return the same delivered value
  without blocking."
  {:added "1.1"}
  []
  (let [d (java.util.concurrent.CountDownLatch. 1)
        v (atom nil)]
    ^{:delivered?
      (fn []
        (locking d
          (zero? (.getCount d))))}
    (reify 
     clojure.lang.IDeref
      (deref [_] (.await d) @v)
     clojure.lang.IFn
      (invoke [this x]
        (locking d
          (if (pos? (.getCount d))
            (do (reset! v x)
                (.countDown d)
                this)
            (throw (IllegalStateException. "Multiple deliver calls to a promise"))))))))

(defn delivered?
  "Alpha - subject to change.
  Returns true if promise has been delivered, else false"
  [p]
  (if-let [f (:delivered? (meta p))]
   (f)))

(defmacro gen-methods [& methods]
  (list* 'do
     (map (fn [method#]
            (let [fn-name (symbol (.toUpperCase (name method#)))
                  fn-doc (str "Sends asynchronously HTTP " fn-name " request to url.
  Returns a map:
  - :id      - unique ID of request
  - :status  - promise that once status is received is delivered, contains lazy map of:
    - :code     - response code
    - :msg      - response message
    - :protocol - protocol with version
    - :major    - major version of protocol
    - :minor    - minor version of protocol
  - :headers - promise that once headers are received is delivered, contains lazy map of:
    - :server - header names are keyworded, values stay not changed
  - :body    - body of response, depends on request type, might be ByteArrayOutputStream
               or lazy sequence, use conveniece methods to extract it, like string
  - :done    - promise that is delivered once receiving response has finished
  - :error   - promise that is delivered if requesting resource failed, once delivered
               will contain Throwable.
  Arguments:
  - url     - URL to request
  - options - keyworded arguments:
    :query   - map of query parameters
    :headers - map of headers
    :body    - body
    :cookies - cookies to send
    :proxy   - map with proxy configuration to be used (:host and :port)
    :auth    - map with authentication to be used
      :type     - either :basic or :digest
      :user     - user name to be used
      :password - password to be used
      :realm    - realm name to authenticate in")]
              `(defn ~fn-name ~fn-doc [#^String ~'url & {:as ~'options}]
                 (apply r/execute-request
                        (apply r/prepare-request ~method# ~'url (apply concat ~'options))
                        (apply concat r/*default-callbacks*)))))
          methods)))
