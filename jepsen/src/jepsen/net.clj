(ns jepsen.net
  "Controls network manipulation."
  (:use jepsen.control)
  (:require [jepsen.control.net :as control.net]
            [clojure.tools.logging :refer [debug info warn]]))

(defprotocol Net
  (drop! [net test src dest] "Drop traffic between nodes src and dest.")
  (heal! [net test]          "End all traffic drops."))

(def noop
  "Does nothing."
  (reify Net
    (drop! [net test src dest])
    (heal! [net test])))

(def iptables
  "Default iptables (assumes we control everything)."
  (reify Net
    (drop! [net test src dest]
      (on dest (su (exec :iptables :-A :INPUT :-s (control.net/ip src) :-j :DROP :-w))))

    (heal! [net test]
      (on-many (:nodes test) (su
                               (exec :iptables :-F :-w)
                               (exec :iptables :-X :-w))))))

(def tc-slow-net
  "Slows down the network."
  (reify Net
    (drop! [net test src dest]
      (info "slowing down networking on " src "to " dest)
      (on src (su (exec (control.net/slow (control.net/ip dest))))))

    (heal! [net test]
      (on-many (:nodes test) (su (exec (control.net/fast)))))))

(def tc-drop-packets
  "Simulate packet drops."
  (reify Net
    (drop! [net test src dest]
      (info "initiating drops on " dest)
      (on dest (su (exec (control.net/flaky)))))

    (heal! [net test]
      (on-many (:nodes test) (su (exec (control.net/fast)))))))

(def ipfilter
  "IPFilter rules"
  (reify Net
    (drop! [net test src dest]
      (on dest (su (exec :echo :block :in :from src :to :any | :ipf :-f :-))))
    (heal! [net test]
      (on-many (:nodes test) (su
                              (exec :ipf :-Fa))))))
