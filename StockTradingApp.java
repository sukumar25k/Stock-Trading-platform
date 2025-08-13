// Stock Trading Simulation in Java
// Spec-compliant version for TASK 2
// Clean, structured, and runnable as a single file
// Author: Anshuman Sinha (refactor & structure by ChatGPT)

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/* ----------------------------- MODELS ----------------------------- */

class Stock {
    final String symbol;
    double price;
    final List<Double> priceHistory = new ArrayList<>();

    public Stock(String symbol, double price) {
        this.symbol = symbol;
        this.price = price;
        priceHistory.add(price);
    }

    // Random walk: -5% .. +5%
    public void tickRandom() {
        double pct = (Math.random() * 10.0) - 5.0;
        double newPrice = Math.max(1.0, price * (1.0 + pct / 100.0));
        updatePrice(round2(newPrice));
    }

    public void updatePrice(double newPrice) {
        this.price = newPrice;
        priceHistory.add(newPrice);
        if (priceHistory.size() > 5000) priceHistory.remove(0); // keep it bounded
    }

    public double sma(int period) {
        if (priceHistory.size() < period) return -1;
        double sum = 0;
        for (int i = priceHistory.size() - period; i < priceHistory.size(); i++) sum += priceHistory.get(i);
        return round2(sum / period);
    }

    public double ema(int period) {
        if (priceHistory.size() < period) return -1;
        double k = 2.0 / (period + 1);
        double ema = priceHistory.get(priceHistory.size() - period);
        for (int i = priceHistory.size() - period + 1; i < priceHistory.size(); i++) {
            ema = priceHistory.get(i) * k + ema * (1 - k);
        }
        return round2(ema);
    }

    static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}

class User {
    final String username;
    final String password;
    double cash;              // available cash
    double marginDebt = 0;    // borrowed amount (for margin buys & short proceeds)
    final Map<String, Integer> portfolio = new HashMap<>(); // symbol -> qty
    final List<String> history = new ArrayList<>();

    public User(String username, String password, double startingCash) {
        this.username = username;
        this.password = password;
        this.cash = startingCash;
    }

    // Simple 2× buying power: cash + maxBorrow where maxBorrow = cash
    public double buyingPower() {
        double maxBorrow = cash;          // 2x rule
        return cash + Math.max(0, maxBorrow - marginDebt);
    }

    public void addPosition(String sym, int qty) {
        portfolio.put(sym, portfolio.getOrDefault(sym, 0) + qty);
        if (portfolio.get(sym) == 0) portfolio.remove(sym);
    }

    public int position(String sym) { return portfolio.getOrDefault(sym, 0); }

    public void log(String s) { history.add(s); }

    public void showPortfolio(Map<String, Stock> market) {
        System.out.println("\n--- Portfolio: " + username + " ---");
        double equity = 0;
        for (Map.Entry<String,Integer> e : portfolio.entrySet()) {
            Stock st = market.get(e.getKey());
            double val = st.price * e.getValue();
            equity += val;
            System.out.println(e.getKey() + ": " + e.getValue() + " shares @ $" + st.price + " (Value $" + Stock.round2(val) + ")");
        }
        double netWorth = cash + equity - marginDebt;
        System.out.println("Cash: $" + Stock.round2(cash));
        System.out.println("Margin Debt: $" + Stock.round2(marginDebt));
        System.out.println("Equity Value: $" + Stock.round2(equity));
        System.out.println("Net Worth: $" + Stock.round2(netWorth));
        System.out.println("--------------------------------");
    }
}

enum Side { BUY, SELL }
enum ExecType { MARKET, LIMIT }

class Order {
    static long NEXT_ID = 1;
    final long id = NEXT_ID++;
    final User user;
    final String symbol;
    final Side side;
    final ExecType type;
    int qty;                 // remaining qty
    final double limitPrice; // for LIMIT; ignored for MARKET
    final long ts = System.currentTimeMillis();

    public Order(User user, String symbol, Side side, ExecType type, int qty, double limitPrice) {
        this.user = user;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.qty = qty;
        this.limitPrice = limitPrice;
    }
}

/* ---------------------------- EXCHANGE ---------------------------- */

class Exchange {
    final Map<String, Stock> market;
    final PriorityQueue<Order> buyBook;
    final PriorityQueue<Order> sellBook;

    public Exchange(Map<String, Stock> market) {
        this.market = market;
        // Highest price first, FIFO by timestamp
        buyBook = new PriorityQueue<>((a,b) -> {
            int byPrice = Double.compare(b.limitPrice, a.limitPrice);
            return byPrice != 0 ? byPrice : Long.compare(a.ts, b.ts);
        });
        // Lowest price first, FIFO by timestamp
        sellBook = new PriorityQueue<>((a,b) -> {
            int byPrice = Double.compare(a.limitPrice, b.limitPrice);
            return byPrice != 0 ? byPrice : Long.compare(a.ts, b.ts);
        });
    }

    public void placeMarket(User u, String symbol, Side side, int qty) {
        Stock s = market.get(symbol);
        if (s == null || qty <= 0) { System.out.println("Invalid market order."); return; }
        if (side == Side.BUY) {
            double cost = s.price * qty;
            double bp = u.buyingPower();
            if (bp + 1e-9 < cost) { System.out.println("❌ Not enough buying power."); return; }
            // Use cash first, borrow if needed
            double borrow = Math.max(0, cost - u.cash);
            if (borrow > 0) { u.marginDebt += borrow; u.cash = 0; }
            else { u.cash -= cost; }
            u.addPosition(symbol, qty);
            u.log("BUY MKT " + qty + " " + symbol + " @ $" + s.price + " (cost $" + Stock.round2(cost) + ")");
            System.out.println("✅ Executed: BUY " + qty + " " + symbol + " @ $" + s.price);
        } else {
            if (u.position(symbol) < qty) { System.out.println("❌ Not enough shares to sell."); return; }
            double proceeds = s.price * qty;
            u.addPosition(symbol, -qty);
            // Proceeds reduce debt first, then add to cash
            double repay = Math.min(u.marginDebt, proceeds);
            u.marginDebt -= repay;
            u.cash += (proceeds - repay);
            u.log("SELL MKT " + qty + " " + symbol + " @ $" + s.price + " (proceeds $" + Stock.round2(proceeds) + ")");
            System.out.println("✅ Executed: SELL " + qty + " " + symbol + " @ $" + s.price);
        }
    }

    public void placeLimit(Order order) {
        if (order.type != ExecType.LIMIT || order.qty <= 0 || market.get(order.symbol) == null) {
            System.out.println("Invalid limit order."); return;
        }
        if (order.side == Side.BUY) {
            buyBook.add(order);
        } else {
            // Validate seller has enough shares *now*; (simple model—no reservation across time)
            if (order.user.position(order.symbol) < order.qty) {
                System.out.println("❌ Not enough shares to place sell order.");
                return;
            }
            sellBook.add(order);
        }
        System.out.println("📝 Placed LIMIT " + order.side + " " + order.qty + " " + order.symbol + " @ $" + order.limitPrice + " (id " + order.id + ")");
        match();
    }

    // Simple price-time priority matching for LIMIT orders
    public void match() {
        while (!buyBook.isEmpty() && !sellBook.isEmpty()) {
            Order buy = buyBook.peek();
            Order sell = sellBook.peek();
            if (buy.limitPrice + 1e-9 < sell.limitPrice) break; // no cross

            int tradeQty = Math.min(buy.qty, sell.qty);
            double tradePrice = sell.limitPrice; // price = resting order's price (sell side)

            // Validate at execution time
            if (sell.user.position(sell.symbol) < tradeQty) { sellBook.poll(); continue; }
            double cost = tradePrice * tradeQty;
            double bp = buy.user.buyingPower();
            if (bp + 1e-9 < cost) { buyBook.poll(); continue; } // skip underfunded buy

            // Execute: BUYER
            double borrow = Math.max(0, cost - buy.user.cash);
            if (borrow > 0) { buy.user.marginDebt += borrow; buy.user.cash = 0; } else { buy.user.cash -= cost; }
            buy.user.addPosition(buy.symbol, tradeQty);
            buy.user.log("BUY LMT FILL " + tradeQty + " " + buy.symbol + " @ $" + Stock.round2(tradePrice));

            // Execute: SELLER
            sell.user.addPosition(sell.symbol, -tradeQty);
            double repay = Math.min(sell.user.marginDebt, cost);
            sell.user.marginDebt -= repay;
            sell.user.cash += (cost - repay);
            sell.user.log("SELL LMT FILL " + tradeQty + " " + sell.symbol + " @ $" + Stock.round2(tradePrice));

            // Decrement or remove orders
            buy.qty -= tradeQty; sell.qty -= tradeQty;
            if (buy.qty == 0) buyBook.poll();
            if (sell.qty == 0) sellBook.poll();

            System.out.println("🔗 Matched " + tradeQty + " " + buy.symbol + " @ $" + Stock.round2(tradePrice));
        }
    }

    public void showBook(String symbol) {
        System.out.println("\nOrder Book for " + symbol + ":");
        System.out.println("  Buys (price desc):");
        buyBook.stream().filter(o -> o.symbol.equals(symbol))
                .sorted((a,b)->{
                    int p = Double.compare(b.limitPrice, a.limitPrice);
                    return p!=0?p:Long.compare(a.ts,b.ts);
                }).forEach(o -> System.out.println("    " + o.qty + " @ " + o.limitPrice + " (u:" + o.user.username + ", id:" + o.id + ")"));
        System.out.println("  Sells (price asc):");
        sellBook.stream().filter(o -> o.symbol.equals(symbol))
                .sorted((a,b)->{
                    int p = Double.compare(a.limitPrice, b.limitPrice);
                    return p!=0?p:Long.compare(a.ts,b.ts);
                }).forEach(o -> System.out.println("    " + o.qty + " @ " + o.limitPrice + " (u:" + o.user.username + ", id:" + o.id + ")"));
    }
}

/* ---------------------------- APPLICATION ---------------------------- */

public class StockTradingApp {
    static final Scanner in = new Scanner(System.in);
    static final Map<String, Stock> MARKET = new ConcurrentHashMap<>();
    static final Map<String, User> USERS = new HashMap<>();
    static final List<String> NEWS = new ArrayList<>();
    static Exchange EX;
    static Timer marketTimer;

    public static void main(String[] args) {
        seedMarket();
        EX = new Exchange(MARKET);
        startMarket();

        System.out.println("=== Stock Trading Platform (Simulation) ===");
        while (true) {
            System.out.println("\n1) Register  2) Login  3) Exit");
            int ch = readInt();
            if (ch == 1) register();
            else if (ch == 2) {
                User u = login();
                if (u != null) userMenu(u);
            } else if (ch == 3) {
                stopMarket();
                System.out.println("Bye!");
                return;
            } else System.out.println("Invalid choice.");
        }
    }

    /* ------------------------- Menus & Actions ------------------------- */

    static void userMenu(User u) {
        while (true) {
            System.out.println("\n-- Menu (" + u.username + ") --");
            System.out.println("1) View Market  2) View Portfolio  3) Buy (MKT)  4) Sell (MKT)");
            System.out.println("5) Place Limit  6) Show Book  7) SMA/EMA  8) News  9) History");
            System.out.println("10) Margin Buy  11) Short Sell  12) Cover Short (Market)  13) Logout");
            int ch = readInt();
            switch (ch) {
                case 1 -> showMarket();
                case 2 -> u.showPortfolio(MARKET);
                case 3 -> buyMarket(u);
                case 4 -> sellMarket(u);
                case 5 -> placeLimit(u);
                case 6 -> showBook();
                case 7 -> indicators();
                case 8 -> showNews();
                case 9 -> showHistory(u);
                case 10 -> marginBuy(u);
                case 11 -> shortSell(u);
                case 12 -> coverShort(u);
                case 13 -> { return; }
                default -> System.out.println("Invalid.");
            }
        }
    }

    static void buyMarket(User u) {
        System.out.print("Symbol: "); String sym = in.next().toUpperCase();
        System.out.print("Qty: "); int qty = readInt();
        EX.placeMarket(u, sym, Side.BUY, qty);
    }

    static void sellMarket(User u) {
        System.out.print("Symbol: "); String sym = in.next().toUpperCase();
        System.out.print("Qty: "); int qty = readInt();
        EX.placeMarket(u, sym, Side.SELL, qty);
    }

    static void placeLimit(User u) {
        System.out.print("Symbol: "); String sym = in.next().toUpperCase();
        System.out.print("Side (buy/sell): "); String s = in.next().toLowerCase();
        System.out.print("Qty: "); int qty = readInt();
        System.out.print("Limit Price: "); double px = readDouble();
        Side side = s.startsWith("b") ? Side.BUY : Side.SELL;
        Order order = new Order(u, sym, side, ExecType.LIMIT, qty, Stock.round2(px));
        EX.placeLimit(order);
    }

    static void showBook() {
        System.out.print("Symbol: "); String sym = in.next().toUpperCase();
        EX.showBook(sym);
    }

    static void indicators() {
        System.out.print("Symbol: "); String sym = in.next().toUpperCase();
        System.out.print("SMA period: "); int sp = readInt();
        System.out.print("EMA period: "); int ep = readInt();
        Stock s = MARKET.get(sym);
        if (s == null) { System.out.println("No such symbol."); return; }
        System.out.println("SMA(" + sp + ") = $" + s.sma(sp));
        System.out.println("EMA(" + ep + ") = $" + s.ema(ep));
    }

    static void showNews() {
        System.out.println("\n--- Market News ---");
        for (String n : NEWS) System.out.println("• " + n);
    }

    static void showHistory(User u) {
        System.out.println("\n--- " + u.username + " History ---");
        if (u.history.isEmpty()) System.out.println("(empty)");
        for (String h : u.history) System.out.println(h);
    }

    // Simple margin: force-buy using buying power
    static void marginBuy(User u) {
        System.out.print("Symbol: "); String sym = in.next().toUpperCase();
        System.out.print("Qty: "); int qty = readInt();
        Stock s = MARKET.get(sym);
        if (s == null || qty <= 0) { System.out.println("Invalid."); return; }
        double cost = s.price * qty;
        double bp = u.buyingPower();
        if (bp + 1e-9 < cost) { System.out.println("❌ Not enough buying power."); return; }
        double borrow = Math.max(0, cost - u.cash);
        if (borrow > 0) { u.marginDebt += borrow; u.cash = 0; } else { u.cash -= cost; }
        u.addPosition(sym, qty);
        u.log("MARGIN BUY " + qty + " " + sym + " @ $" + s.price + " (borrow $" + Stock.round2(borrow) + ")");
        System.out.println("✅ MARGIN BUY executed.");
    }

    // Short sell: borrow shares, sell now — here we don't track borrow inventory, just proceeds & negative position
    static void shortSell(User u) {
        System.out.print("Symbol: "); String sym = in.next().toUpperCase();
        System.out.print("Qty: "); int qty = readInt();
        Stock s = MARKET.get(sym);
        if (s == null || qty <= 0) { System.out.println("Invalid."); return; }
        double proceeds = s.price * qty;
        // short proceeds increase cash; require them as debt collateral
        u.cash += proceeds;
        u.marginDebt += proceeds;     // liability equal to proceeds (simple model)
        u.addPosition(sym, -qty);     // negative position indicates short
        u.log("SHORT SELL " + qty + " " + sym + " @ $" + s.price + " (proceeds $" + Stock.round2(proceeds) + ")");
        System.out.println("✅ SHORT SELL executed.");
    }

    // Cover short: buy back shares at market to close/reduce short position
    static void coverShort(User u) {
        System.out.print("Symbol: "); String sym = in.next().toUpperCase();
        System.out.print("Qty to cover: "); int qty = readInt();
        Stock s = MARKET.get(sym);
        int shortQty = -Math.min(0, u.position(sym)); // positive number if short
        if (s == null || qty <= 0 || shortQty <= 0) { System.out.println("Nothing to cover."); return; }
        qty = Math.min(qty, shortQty);
        double cost = s.price * qty;
        if (u.cash + 1e-9 < cost) { System.out.println("❌ Not enough cash to cover."); return; }
        u.cash -= cost;
        u.addPosition(sym, qty); // will move towards zero
        // reduce debt by proceeds equivalent for the shares covered
        double repay = Math.min(u.marginDebt, s.price * qty);
        u.marginDebt -= repay;
        u.log("COVER " + qty + " " + sym + " @ $" + s.price + " (cost $" + Stock.round2(cost) + ")");
        System.out.println("✅ COVER executed.");
    }

    /* ------------------------- Market Plumbing ------------------------- */

    static void seedMarket() {
        MARKET.put("AAPL", new Stock("AAPL", 150));
        MARKET.put("TSLA", new Stock("TSLA", 800));
        MARKET.put("GOOG", new Stock("GOOG", 2800));
        MARKET.put("MSFT", new Stock("MSFT", 300));

        NEWS.add("THIS IS DEVELOPED BY SUKUMAR.");
        NEWS.add("LOVE CODING.");
        NEWS.add("THINK TWICE BEFORE YOU CODING.");
        NEWS.add("WELOCOME.");
    }

    static void startMarket() {
        marketTimer = new Timer("market", true);
        marketTimer.scheduleAtFixedRate(new TimerTask() {
            Random r = new Random();
            @Override public void run() {
                for (Stock s : MARKET.values()) s.tickRandom();
                // random news ping
                if (r.nextInt(5) == 0) {
                    String n = NEWS.get(r.nextInt(NEWS.size()));
                    System.out.println("\n🗞  News Flash: " + n);
                }
            }
        }, 0, 4000); // update every 4 seconds
    }

    static void stopMarket() { if (marketTimer != null) marketTimer.cancel(); }

    static void showMarket() {
        System.out.println("\n--- Market ---");
        MARKET.values().stream()
                .sorted(Comparator.comparing(s -> s.symbol))
                .forEach(s -> System.out.println(s.symbol + ": $" + s.price));
    }

    /* --------------------------- Utilities --------------------------- */

    static int readInt() {
        while (!in.hasNextInt()) { System.out.print("Enter a number: "); in.next(); }
        return in.nextInt();
    }

    static double readDouble() {
        while (!in.hasNextDouble()) { System.out.print("Enter a number: "); in.next(); }
        return in.nextDouble();
    }

    static void register() {
        System.out.print("Username: "); String u = in.next();
        System.out.print("Password: "); String p = in.next();
        if (USERS.containsKey(u)) { System.out.println("Username taken."); return; }
        USERS.put(u, new User(u, p, 10_000));
        System.out.println("✅ Registered. Starting cash: $10,000");
    }

    static User login() {
        System.out.print("Username: "); String u = in.next();
        System.out.print("Password: "); String p = in.next();
        User usr = USERS.get(u);
        if (usr != null && Objects.equals(usr.password, p)) { System.out.println("✅ Login successful."); return usr; }
        System.out.println("❌ Invalid credentials."); return null;
    }
}
