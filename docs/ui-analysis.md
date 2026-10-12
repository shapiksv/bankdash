# BankDash — UI Analysis

Source: Figma file `bENjnEM9EDFhCPboeGukM8` (BankDash — Dashboard UI Kit), page **😍 Design** (`0:1`).

## File overview

- **Real layers, not images.** Screens are built from text, vector charts and groups. Only photos (avatars) and some icons are image fills.
- **Pages:**
  - **👋 Hello** (`217:58`) — cover page (promo banner, mostly a flattened image).
  - **😍 Design** (`0:1`) — all screens, in three breakpoints (30 frames):
    - **Desktop 1440:** Main Dashboard (`78:351`), Transaction (`105:0`), Accounts (`101:0`), Investments (`104:60`), Credit Cards (`101:371`), Loan (`101:677`), Services (`112:941`), Setting Page 1 / 2 / 3 (`112:279`, `112:504`, `112:699`).
    - **Tablet 1024:** the same 10 screens (suffix "Tab").
    - **Mobile 375:** the same 10 screens (suffix "for Mobile"); the sidebar is replaced by a ☰ menu.
- **Things to know before implementation:**
  - No components (0 instances) — everything is plain groups. Cards, table rows, menu items etc. have to be turned into components in code.
  - No prototype interactions (0 reactions). Actions below are inferred from visual elements (buttons, links, inputs), not from prototype links.
  - No auto-layout; all positions are absolute.

## Shared layout

- **Header:** page title, "Search for something" search input, settings button, notifications button, user avatar.
- **Sidebar:** Dashboard, Transactions, Accounts, Investments, Credit Cards, Loans, Services, My Privileges, Setting. The active item is marked with a left accent bar.

## Screens

### Dashboard (Overview)

**Data**
- My Cards — 2 cards: balance, card holder, valid thru, masked number.
- Recent Transaction — 3 latest transactions: name, date, amount (±).
- Weekly Activity — bar chart Deposit / Withdraw by day of week (Sat–Fri).
- Expense Statistics — pie chart: Entertainment 30%, Bill Expense 15%, Investment 20%, Others 35%.
- Quick Transfer — contacts with photo, name, position.
- Balance History — line chart (Jul–Jan).

**Actions**
- "See All" on My Cards.
- Quick Transfer: select a recipient (arrow to scroll the list), enter amount, **Send**.

### Transactions

**Data**
- My Cards — 2 cards.
- My Expense — monthly bar chart (Aug–Jan), current month highlighted ($12,500).
- Recent Transactions table: Description, Transaction ID, Type, Card, Date, Amount, Receipt.

**Actions**
- "+ Add Card".
- Filter tabs: **All Transactions / Income / Expense**.
- **Download** receipt per row.
- Pagination: Previous / 1–4 / Next.

### Accounts

**Data**
- 4 KPI tiles: My Balance, Income, Expense, Total Saving.
- Last Transaction — name, type, card, status (Pending / Completed), amount, date.
- My Card — 1 card.
- Debit & Credit Overview — weekly bar chart with summary ("$7,560 Debited & $5,420 Credited in this Week").
- Invoices Sent — recipient, amount, time ago.

**Actions**
- "See All" on My Card. Otherwise read-only.

### Investments

**Data**
- 3 KPI tiles: Total Invested Amount, Number of Investments, Rate of Return.
- Yearly Total Investment — chart, 2016–2021.
- Monthly Revenue — chart, 2016–2021.
- My Investment — company, category, investment value, return value (%).
- Trending Stock table: SL No, Name, Price, Return.

**Actions**
- None visible — read-only screen.

### Credit Cards

**Data**
- My Cards — 3 cards.
- Card Expense Statistics — chart by bank (DBL, BRC, ABM, MCP).
- Card List — card type, bank, card number, name on card.

**Actions**
- **View Details** per card in Card List.
- **Add New Card** form: Card Type, Name On Card, Card Number, Expiration Date → **Add Card**.
- Card Setting: Block Card, Change Pin Code, Add to Google Pay, Add to Apple Pay, Add to Apple Store.

### Loans

**Data**
- 4 loan type tiles with amounts: Personal Loans, Corporate Loans, Business Loans, Custom Loans.
- Active Loans Overview table: SL No, Loan Money, Left to repay, Duration, Interest rate, Installment, plus a Total row.

**Actions**
- **Repay** per row.
- "Choose Money" on the Custom Loans tile.

### Services

**Data**
- 3 promo tiles: Life Insurance, Shopping, Safety.
- Bank Services List — 6 services (Business loans, Checking accounts, Savings accounts, Debit and credit cards, Life Insurance, Business loans).

**Actions**
- **View Details** per service.

### Setting

Tabs: **Edit Profile / Preferences / Security**.

- **Edit Profile** — avatar (editable) and fields: Your Name, User Name, Email, Password, Date of Birth, Present Address, Permanent Address, City, Postal Code, Country → **Save**.
- **Preferences** — Currency, Time Zone, 3 notification toggles → **Save**.
- **Security** — Two-factor Authentication toggle, Change Password (Current Password, New Password) → **Save**.

## Open questions

- **No Sign Up / Login screens** on the Design page. There is only a mobile "Sign Up" frame (`218:279`) on the cover page.
- **No screen for "My Privileges"**, although it is in the sidebar.
- **Typos and placeholders in the mockup:**
  - "Diposit" (Weekly Activity legend) → "Deposit".
  - "$125,0000" in the Loans Total row.
  - "Namain Card" (Card List) → "Name on Card".
  - "Envestment Value" (My Investment) → "Investment Value".
  - Lorem Ipsum / "Many publishing" placeholder text in Bank Services List.
  - "Business loans" appears twice in Bank Services List.
