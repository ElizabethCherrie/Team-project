# IPOS-SA to IPOS-CA Integration Contract

This document defines the recommended integration between Team A's `IPOS-SA` and Team B's `IPOS-CA`.

The key principle is:

- `IPOS-SA` and `IPOS-CA` keep separate databases.
- The subsystems communicate through HTTP endpoints.
- Team B should not directly read or write Team A's SQLite database.

## Local Runtime Assumption

During the prototype demo, `IPOS-SA` runs locally on:

- `http://localhost:8080`

The frontend pages are served by the same Java process.

## Authentication and Handoff

### Login Endpoint

`POST /api/auth/login`

Example request:

```json
{
  "username": "city",
  "password": "northampton"
}
```

Example response:

```json
{
  "username": "city",
  "role": "MERCHANT",
  "merchantId": "ACC0001",
  "sessionToken": "..."
}
```

### Session Bootstrap Endpoint

`GET /api/auth/session`

Required header:

```text
X-Session-Token: <session token>
```

This allows the browser frontend to recover the session from a token.

### Browser Handoff into Team A Website

After Team B authenticates a merchant through `IPOS-SA`, Team B can open:

```text
http://localhost:8080/merchant.html?sessionToken=<token>
```

The Team A frontend will bootstrap the session automatically and load the merchant dashboard.

## Catalogue Access

### List Products

`GET /api/products`

Returns all products currently available from `IPOS-SA`.

### Search Products

`GET /api/products/search?q=<keyword>`

Used when Team B wants filtered catalogue lookup.

## Ordering

### Create Order

`POST /api/orders`

Required header:

```text
X-Session-Token: <merchant session token>
```

Example request:

```json
{
  "merchantId": "ACC0001",
  "items": [
    { "productId": "10000001", "quantity": 5 },
    { "productId": "10000003", "quantity": 2 }
  ]
}
```

Notes:

- the token must belong to the same merchant as `merchantId`
- `IPOS-SA` validates account state and credit limit
- stock is reduced in `IPOS-SA`
- an invoice can then be generated or retrieved

### List Orders

`GET /api/orders?merchantId=ACC0001`

For merchant users, `IPOS-SA` automatically restricts visible orders to the logged-in merchant.

### Get One Order

`GET /api/orders/<orderId>`

## Invoices

### List Invoices

`GET /api/invoices?merchantId=ACC0001`

### Get One Invoice

`GET /api/invoices/<invoiceId>`

This returns printable text suitable for display or printing.

## Merchant Account Data

### Get Merchant Balance

`GET /api/merchants/<merchantId>/balance`

This is useful if Team B wants to show current balance or account warnings inside its client UI.

### Get Merchant Details

`GET /api/merchants/<merchantId>`

## Recommended Team B Flow

For a merchant using `IPOS-CA`:

1. submit credentials to `POST /api/auth/login`
2. receive `sessionToken`
3. if Team B wants to open Team A UI directly, launch:

```text
http://localhost:8080/merchant.html?sessionToken=<token>
```

4. if Team B wants to stay in its own UI, use the same token for:
   - `GET /api/products`
   - `POST /api/orders`
   - `GET /api/orders/...`
   - `GET /api/invoices/...`

## What Team B Should Not Do

- do not connect directly to `IPOS-SA/data/ipos-sa.db`
- do not assume Team A table structure is shared contract
- do not mutate Team A stock/order/invoice data outside Team A endpoints

## Notes for Demo

- if Team B launches Team A pages, Team A server must already be running
- Team A should restart with a clean database before the final demo where needed
