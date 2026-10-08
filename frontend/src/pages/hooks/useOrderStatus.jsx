import { useCallback, useEffect, useRef, useState } from "react";
import API from "../../utils/api";

const SETTLED = ["PAID", "PAYMENT_FAILED", "EXPIRED"];

// Loads one order. With `waitForPayment`, keeps re-checking every `intervalMs` while it is still
// PENDING, because a verified payment reaches the order a moment later (payment-service ->
// RabbitMQ -> order-service). Gives up after `timeoutMs` and reports `timedOut`.
export default function useOrderStatus(orderId, { waitForPayment = false, intervalMs = 2000, timeoutMs = 30000 } = {}) {
  const [order, setOrder] = useState(null);
  const [error, setError] = useState(null);
  const [polling, setPolling] = useState(waitForPayment);
  const [timedOut, setTimedOut] = useState(false);
  const [fetchedAt, setFetchedAt] = useState(null);
  const timer = useRef(null);

  const load = useCallback(async () => {
    try {
      const res = await API.get(`/orders/by-order-id/${encodeURIComponent(orderId)}`);
      setOrder(res.data.data);
      setFetchedAt(Date.now());
      setError(null);
      return res.data.data;
    } catch (err) {
      setError(err.response?.data?.message || "Could not load the order");
      return null;
    }
  }, [orderId]);

  useEffect(() => {
    let cancelled = false;
    const started = Date.now();

    const tick = async () => {
      const current = await load();
      if (cancelled) return;
      const keepWaiting = waitForPayment && (!current || !SETTLED.includes(current.status));
      if (!keepWaiting) {
        setPolling(false);
      } else if (Date.now() - started >= timeoutMs) {
        setPolling(false);
        setTimedOut(true);
      } else {
        timer.current = setTimeout(tick, intervalMs);
      }
    };
    tick();

    return () => {
      cancelled = true;
      clearTimeout(timer.current);
    };
  }, [load, waitForPayment, intervalMs, timeoutMs]);

  return { order, error, polling, timedOut, fetchedAt, reload: load };
}
