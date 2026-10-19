package com.cardbench.disputes.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cardbench.disputes.domain.CardTransaction;
import com.cardbench.disputes.domain.Cardmember;
import com.cardbench.disputes.domain.PaymentCard;
import com.cardbench.disputes.repository.CardTransactionRepository;
import com.cardbench.disputes.repository.CardmemberRepository;
import com.cardbench.disputes.repository.PaymentCardRepository;

@Component
public class DemoData implements CommandLineRunner {
    private static final String[] NAMES = {
            "Avery Morgan", "Jordan Lee", "Riley Chen", "Casey Patel", "Taylor Brooks", "Morgan Ellis"
    };
    private static final String[] MERCHANTS = {
            "Northstar Market", "Juniper Cafe", "Harborline Transit", "Cedar & Stone", "Brightpath Books",
            "Cloudberry Outfitters", "Copper Kettle", "Lumen Home", "Fieldwork Supply", "Maple Street Cinema"
    };
    private static final String[] CATEGORIES = {
            "Groceries", "Dining", "Transit", "Dining", "Retail", "Retail", "Dining", "Home", "Retail", "Entertainment"
    };

    private final CardmemberRepository cardmembers;
    private final PaymentCardRepository cards;
    private final CardTransactionRepository transactions;
    private final Clock clock;

    public DemoData(CardmemberRepository cardmembers, PaymentCardRepository cards,
                    CardTransactionRepository transactions, Clock clock) {
        this.cardmembers = cardmembers;
        this.cards = cards;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (cardmembers.count() != 0) {
            return;
        }

        List<Cardmember> members = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            String id = "member-%02d".formatted(i + 1);
            members.add(cardmembers.save(new Cardmember(id, NAMES[i], id + "@cardbench.example")));
        }

        List<PaymentCard> issuedCards = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Cardmember member = members.get(i % members.size());
            String id = "card-%02d".formatted(i + 1);
            issuedCards.add(cards.save(new PaymentCard(id, "Everyday card %02d".formatted(i + 1), member)));
        }

        LocalDate today = LocalDate.now(clock);
        for (int i = 0; i < 80; i++) {
            int number = i + 1;
            PaymentCard card = issuedCards.get(i % issuedCards.size());
            BigDecimal amount = BigDecimal.valueOf(735L + (number * 1187L % 48265L), 2);
            LocalDate postingDate = today.minusDays((number * 37L) % 200L);
            transactions.save(new CardTransaction(
                    "txn-%03d".formatted(number),
                    MERCHANTS[i % MERCHANTS.length],
                    CATEGORIES[i % CATEGORIES.length],
                    amount,
                    postingDate,
                    card));
        }
    }
}
