import { useEffect, useState } from 'react';
import {
  createOrder, cancelOrder, getOrders, getInventory, getNotifications,
} from './api/ordersApi';
import './index.css';

export default function App() {
  const [inventory, setInventory] = useState([]);
  const [orders, setOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);

  const [productId, setProductId] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [cart, setCart] = useState([]);

  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function refresh() {
    const [inv, ord, notes] = await Promise.all([getInventory(), getOrders(), getNotifications()]);
    if (!inv.ok) {
      setError(inv.data?.message || 'Could not load inventory.');
      return;
    }
    setInventory(inv.data);
    if (ord.ok) setOrders(ord.data);
    if (notes.ok) setNotifications(notes.data);
    setProductId((current) => current || inv.data[0]?.productId || '');
  }

  useEffect(() => { refresh(); }, []);

  const nameOf = (id) => inventory.find((p) => p.productId === id)?.name ?? id;

  function addToCart(e) {
    e.preventDefault();
    const q = Number(quantity);
    if (!productId || q < 1) return;
    setCart((items) => {
      const existing = items.find((i) => i.productId === productId);
      return existing
        ? items.map((i) => (i.productId === productId ? { ...i, quantity: i.quantity + q } : i))
        : [...items, { productId, quantity: q }];
    });
    setQuantity(1);
  }

  function updateCartQuantity(id, value) {
    const q = Math.max(1, Number(value) || 1);
    setCart((items) => items.map((i) => (i.productId === id ? { ...i, quantity: q } : i)));
  }

  function removeFromCart(id) {
    setCart((items) => items.filter((i) => i.productId !== id));
  }

  async function submitOrder() {
    setSubmitting(true);
    setError('');
    setResult(null);

    const { ok, data } = await createOrder(cart);
    if (ok) {
      setResult(data);
      if (data.status === 'CONFIRMED') setCart([]);
    } else {
      setError(data?.message || 'Order could not be placed.');
    }

    await refresh();
    setSubmitting(false);
  }

  async function handleCancel(orderId) {
    setError('');
    const { ok, data } = await cancelOrder(orderId);
    if (!ok) setError(data?.message || `Could not cancel order ${orderId}.`);
    await refresh();
  }

  return (
    <div className="dashboard">
      <h1>Order &amp; Inventory</h1>
      {error && <div className="banner-error">{error}</div>}

      <div className="grid">
        {/* ---------- Cart ---------- */}
        <section className="card">
          <h2>New order</h2>
          <form onSubmit={addToCart} className="cart-form">
            <div className="form-group grow">
              <label htmlFor="product">Product</label>
              <select id="product" value={productId} onChange={(e) => setProductId(e.target.value)}>
                {inventory.map((p) => (
                  <option key={p.productId} value={p.productId}>
                    {p.productId} — {p.name} ({p.stock})
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group qty">
              <label htmlFor="quantity">Quantity</label>
              <input id="quantity" type="number" min="1" value={quantity}
                     onChange={(e) => setQuantity(e.target.value)} />
            </div>
            <button type="submit" className="btn-secondary">Add to cart</button>
          </form>

          {cart.length === 0 ? (
            <p className="muted">Your cart is empty. Add one or more products above.</p>
          ) : (
            <ul className="cart-list">
              {cart.map((i) => (
                <li key={i.productId}>
                  <span className="grow">{i.productId} — {nameOf(i.productId)}</span>
                  <input type="number" min="1" value={i.quantity}
                         onChange={(e) => updateCartQuantity(i.productId, e.target.value)} />
                  <button type="button" className="btn-link" onClick={() => removeFromCart(i.productId)}>
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}

          <button type="button" className="btn-primary" disabled={!cart.length || submitting} onClick={submitOrder}>
            {submitting ? 'Placing order…' : 'Submit order'}
          </button>

          {result && (
            <div className={`result ${result.status === 'CONFIRMED' ? 'result-confirmed' : 'result-rejected'}`}>
              <div className="result-status">Order {result.orderId}: {result.status}</div>
              {result.reason && <div className="result-reason">{result.reason}</div>}
              <ul className="result-items">
                {result.items.map((i) => (
                  <li key={i.productId}>{nameOf(i.productId)} × {i.quantity}: {i.outcome}</li>
                ))}
              </ul>
            </div>
          )}
        </section>

        {/* ---------- Inventory ---------- */}
        <section className="card">
          <h2>Inventory</h2>
          <table>
            <thead>
              <tr><th>ID</th><th>Product</th><th className="num">Stock</th></tr>
            </thead>
            <tbody>
              {inventory.map((p) => (
                <tr key={p.productId} className={p.stock === 0 ? 'row-out' : p.lowStock ? 'row-low' : ''}>
                  <td>{p.productId}</td>
                  <td>{p.name}</td>
                  <td className="num">
                    {p.stock}
                    {p.lowStock && <span className="badge">{p.stock === 0 ? 'Out' : 'Low'}</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        {/* ---------- Order history ---------- */}
        <section className="card">
          <h2>Order history</h2>
          {orders.length === 0 && <p className="muted">No orders yet.</p>}
          <ul className="order-list">
            {orders.map((o) => (
              <li key={o.orderId}>
                <div className="grow">
                  <div>
                    <strong>Order {o.orderId}</strong>{' '}
                    <span className={`status status-${o.status.toLowerCase()}`}>{o.status}</span>
                  </div>
                  <div className="muted">
                    {o.items.map((i) => `${nameOf(i.productId)} × ${i.quantity}`).join(', ')}
                  </div>
                </div>
                {o.status === 'CONFIRMED' && (
                  <button type="button" className="btn-secondary" onClick={() => handleCancel(o.orderId)}>
                    Cancel
                  </button>
                )}
              </li>
            ))}
          </ul>
        </section>

        {/* ---------- Notification feed ---------- */}
        <section className="card">
          <h2>Activity</h2>
          {notifications.length === 0 && <p className="muted">No activity yet.</p>}
          <ul className="feed">
            {notifications.map((n) => (
              <li key={n.notificationId} className={`feed-${n.type.toLowerCase()}`}>
                <span className="grow">{n.message}</span>
                <time>{new Date(n.createdAt).toLocaleTimeString()}</time>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}
