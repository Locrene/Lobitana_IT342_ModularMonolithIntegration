import { useState } from 'react';
import { createOrder } from './api/ordersApi';
import './index.css'
const PRODUCTS = [
  { id: 'P100', label: 'P100 — Wireless Mouse' },
  { id: 'P200', label: 'P200 — Mechanical Keyboard' },
  { id: 'P300', label: 'P300 — USB-C Hub' },
];

export default function App() {
  const [productId, setProductId] = useState(PRODUCTS[0].id);
  const [quantity, setQuantity] = useState(1);
  const [result, setResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    setResult(null);

    const { data } = await createOrder({ productId, quantity: Number(quantity) });
    setResult(data);
    setSubmitting(false);
  }

  return (
    <div className="page">
      <div className="card">
        <h1>Place an Order</h1>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="product">Product</label>
            <select
              id="product"
              value={productId}
              onChange={(e) => setProductId(e.target.value)}
            >
              {PRODUCTS.map((p) => (
                <option key={p.id} value={p.id}>{p.label}</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="quantity">Quantity</label>
            <input
              id="quantity"
              type="number"
              min="1"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
            />
          </div>

          <button type="submit" disabled={submitting}>
            {submitting ? 'Placing order…' : 'Submit Order'}
          </button>
        </form>

        {result && (
          <div className={`result ${result.status === 'CONFIRMED' ? 'result-confirmed' : 'result-rejected'}`}>
            <div className="result-status">{result.status}</div>
            {result.reason && <div className="result-reason">{result.reason}</div>}
            {result.inventory && (
              <div className="result-inventory">
                <strong>{result.inventory.name}</strong> — stock remaining: {result.inventory.stock}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}