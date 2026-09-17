const BASE_URL = 'http://localhost:8080/api';

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    });
  } catch (networkError) {
    return {
      ok: false,
      status: 0,
      data: { status: 'ERROR', message: 'Could not reach the server. Is the backend running?' },
    };
  }

  let data = null;
  try {
    data = await response.json();
  } catch {
    // no JSON body
  }

  return { ok: response.ok, status: response.status, data };
}

export function createOrder(items) {
  return request('/orders', { method: 'POST', body: JSON.stringify({ items }) });
}

export function cancelOrder(orderId) {
  return request(`/orders/${orderId}/cancel`, { method: 'POST' });
}

export const getOrders = () => request('/orders');
export const getInventory = () => request('/inventory');
export const getNotifications = () => request('/notifications');
