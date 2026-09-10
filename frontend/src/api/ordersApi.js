const BASE_URL = 'http://localhost:8080/api';

export async function createOrder({ productId, quantity }) {
  let response;
  try {
    response = await fetch(`${BASE_URL}/orders`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ productId, quantity }),
    });
  } catch (networkError) {
    return {
      ok: false,
      status: 0,
      data: { status: 'ERROR', reason: 'Could not reach the server. Is the backend running?' },
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