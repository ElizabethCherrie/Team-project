# IPOS-SA Demo Script

This script is designed to keep the demo focused, stable, and easy to follow.

## Before the Demo

1. Reset demo data.
2. Build the project.
3. Start `IPOS-SA`.
4. Open:

```text
http://localhost:8080/login.html
```

5. Keep these seeded logins ready:

- `admin / admin123`
- `manager / manager123`
- `ops / ops123`
- `accounts / accounts123`
- `merchant1 / merchant123`

## Demo Flow

### 1. Merchant logs in and browses catalogue

Use:

- `merchant1 / merchant123`

Show:

- merchant dashboard
- product search
- product list
- merchant balance / warnings area

### 2. Merchant places an order

Show:

- order creation using sample order JSON
- successful order creation
- merchant order list

Key points to say:

- account status is checked
- credit limit is checked
- stock is reduced in `IPOS-SA`

### 3. Operations staff processes the order

Log in as:

- `ops / ops123`

Show:

- pending orders
- retrieve the new order
- update status from `ACCEPTED` to `PROCESSING`
- update status from `PROCESSING` to `DISPATCHED`
- enter courier, tracking number, expected delivery
- generate invoice

Then optionally:

- update from `DISPATCHED` to `DELIVERED`

### 4. Merchant views invoice

Log back in as:

- `merchant1 / merchant123`

Show:

- list invoices
- retrieve invoice
- print invoice

### 5. Accounting staff records payment

Log in as:

- `accounts / accounts123`

Show:

- list payments
- record payment for merchant
- re-check merchant balance

### 6. Manager views reports and reminders

Log in as:

- `manager / manager123`

Show:

- low stock report
- turnover report
- stock turnover report
- merchant orders report
- merchant activity report
- merchant invoices report
- company invoices report
- debtor reminders report

Also show:

- merchant search
- view merchant balance
- restore account flow if needed

### 7. Applications flow

Still as manager:

Show:

- create non-commercial application
- list applications
- approve or reject one
- explain that email is logged in the prototype

## If the Examiner Asks About Integration

Say:

- each subsystem keeps its own database
- integration is done via HTTP, not direct shared database access
- `IPOS-CA` can hand off to Team A using a session token URL

## High-Risk Things to Avoid During Demo

- do not show raw local folders or temporary files
- do not show old conflict folders
- do not leave stale session state from previous runs
- do not change order status out of sequence
- do not start with a dirty database

## Quick Recovery Plan

If data is messy:

1. stop the server
2. run the reset script
3. rebuild
4. restart
