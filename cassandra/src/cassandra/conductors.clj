(ns cassandra.conductors
  (:require [clojure.tools.logging :refer :all]
            [jepsen [client :as client]
             [control :as c]
             [util :as util :refer [meh]]]
            [cassandra.core :as cassandra]))

(defn bootstrapper
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (let [bootstrap (:bootstrap test)]
        (if-let [node (first @bootstrap)]
          (do (info node "bootstrapping")
              (swap! bootstrap rest)
              (c/on node (cassandra/start! node test))
              (assoc op :value (str node " bootstrapped")))
          (assoc op :value "no nodes left to bootstrap"))))
    (teardown! [this test] this)))

(defn decommissioner
  []
  (reify client/Client
    (setup! [this test node] this)
    (invoke! [this test op]
      (let [decommission (:decommission test)]
        (if-let [node (some-> test cassandra/live-nodes shuffle (get 3))] ; keep at least RF nodes
          (do (info node "decommissioning")
              (info @decommission "already decommissioned")
              (swap! decommission conj node)
              (meh (cassandra/nodetool node "decommission"))
              (assoc op :value (str node " decommissioned")))
          (assoc op :value "no nodes eligible for decommission"))))
    (teardown! [this test] this)))