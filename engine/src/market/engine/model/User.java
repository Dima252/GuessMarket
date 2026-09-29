package market.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A participant of the system, with a name that is unique in the whole system
 * and an account of its own.
 * <p>
 * Users are no longer read from a file: each one logs in under a name of their
 * own and starts with an empty account, which they fill by depositing money.
 * <p>
 * A user whose balance is below zero is blocked: until they deposit enough to
 * cover the debt they may not act in the system. That state is reachable through
 * orders that were each affordable when they were placed and are executed later.
 * <p>
 * Every movement of money in or out of the account leaves a line in its ledger,
 * which says what happened and what the balance was afterwards.
 */
public final class User {

    private final String name;
    private final Account account;
    private final List<AccountEntry> ledger = new ArrayList<>();

    public User(String name) {
        this.name = name;
        this.account = new Account();
    }

    public String name() {
        return name;
    }

    public double balance() {
        return account.balance();
    }

    public boolean canAfford(double amount) {
        return account.covers(amount);
    }

    /** Money the user puts in from outside the system. */
    public void deposit(double amount) {
        receive(amount, AccountEntryKind.DEPOSIT, "Deposit");
    }

    /** Takes money out of the account, saying why. Nothing is recorded for an amount of zero. */
    public void pay(double amount, AccountEntryKind kind, String description) {
        if (amount == 0.0) {
            return;
        }
        account.withdraw(amount);
        record(kind, description, -amount);
    }

    /** Puts money into the account, saying why. Nothing is recorded for an amount of zero. */
    public void receive(double amount, AccountEntryKind kind, String description) {
        if (amount == 0.0) {
            return;
        }
        account.deposit(amount);
        record(kind, description, amount);
    }

    private void record(AccountEntryKind kind, String description, double amount) {
        ledger.add(new AccountEntry(ledger.size() + 1, kind, description, amount, account.balance()));
    }

    public boolean isBlocked() {
        return account.isNegative();
    }

    /** The lines of the ledger after the given serial number, oldest first. */
    public List<AccountEntry> ledgerAfter(int serial) {
        if (serial >= ledger.size()) {
            return List.of();
        }
        return Collections.unmodifiableList(ledger.subList(Math.max(serial, 0), ledger.size()));
    }

    public int lastLedgerSerial() {
        return ledger.size();
    }

    @Override
    public String toString() {
        return name;
    }
}
