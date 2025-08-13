# Stock-Trading-platform
The Stock Trading Platform is a Java console-based simulation for learning stock market basics. It features live (mock) price updates, buy/sell orders, portfolio tracking, margin trading, short selling, order book, technical indicators, and news feed. Built with OOP, ArrayList, and HashMap, it offers a safe, risk-free trading experience.

# 📈 Stock Trading Platform (Java) — Step-by-Step README

A clean, single-file Java console app that simulates a stock market with:
- Market data (mock prices with random updates)
- Buy/Sell (market & limit with an order book)
- Portfolio tracking, SMA/EMA indicators
- Simple margin buy & short selling
- News feed & transaction history

> **File name:** `StockTradingApp.java` (from the last message)

---

## 1) Prerequisites

1. Install **Java JDK 8+**  
   - Check: `java -version` and `javac -version`
2. Create a folder for the project and place `StockTradingApp.java` inside it.

---

## 2) Compile


If no errors, this produces StockTradingApp.class.

javac StockTradingApp.java

or java StockTradingApp.java


---

## 💻 How to Run

1. **Clone or Download** this repository.
2. Make sure **Java JDK** (version 8+) is installed.
3. Open terminal in the project folder.
4. Compile the program:
   ```bash
   javac StockTradingPlatform.java

######## *** OUT PUT *** #############
=== Stock Trading Platform (Simulation) ===
1) Register  2) Login  3) Exit
1
Username: anshu
Password: 123
Registered successfully.

=== Stock Trading Platform (Simulation) ===
1) Register  2) Login  3) Exit
2
Username: SUKKU
Password: 123
Login successful.

1) View Market  2) View Portfolio  3) Buy  4) Sell  5) SMA/EMA  6) Logout
1

Symbol  Price
AAPL    150.00
TSLA    800.00
GOOG    2800.00
MSFT    300.00

1) View Market  2) View Portfolio  3) Buy  4) Sell  5) SMA/EMA  6) Logout
3
Symbol: AAPL
Quantity: 10
Bought successfully.

1) View Market  2) View Portfolio  3) Buy  4) Sell  5) SMA/EMA  6) Logout
2

Portfolio:
AAPL: 10 shares (1500.00)
Cash: 8500.00, Margin Debt: 0.00, Net Worth: 10000.00


 
