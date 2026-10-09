package com.lubrimanager.dashboard;

import com.lubrimanager.card.CardStatus;
import com.lubrimanager.customer.CustomerStatus;
import com.lubrimanager.dashboard.dto.AgentDaySummary;
import com.lubrimanager.dashboard.dto.DashboardResponse;
import com.lubrimanager.dashboard.dto.TopCustomerRow;
import com.lubrimanager.dashboard.dto.TopProductRow;
import com.lubrimanager.dashboard.dto.UserActivityRow;
import com.lubrimanager.transaction.TransactionStatus;
import com.lubrimanager.transaction.TransactionType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final TransactionStatus VALIDEE = TransactionStatus.VALIDEE;
    private static final TransactionType RECHARGE = TransactionType.RECHARGE;
    private static final TransactionType ACHAT = TransactionType.ACHAT;

    private final DashboardRepository repository;
    private final Clock clock;

    public DashboardService(DashboardRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public DashboardResponse overview() {
        DashboardResponse.Counts counts = new DashboardResponse.Counts(
                repository.countCustomers(),
                repository.countCustomersExcludingStatus(CustomerStatus.ARCHIVE),
                repository.countCards(),
                repository.countCardsByStatus(CardStatus.ACTIVE),
                repository.countCardsByStatus(CardStatus.BLOQUEE));

        return new DashboardResponse(counts,
                period(DashboardPeriods.today(clock)),
                period(DashboardPeriods.currentWeek(clock)),
                period(DashboardPeriods.currentMonth(clock)));
    }

    private DashboardResponse.Period period(DashboardPeriods.Range r) {
        return new DashboardResponse.Period(
                repository.sumAmount(RECHARGE, VALIDEE, r.from(), r.to()),
                repository.countAmount(RECHARGE, VALIDEE, r.from(), r.to()),
                repository.sumAmount(ACHAT, VALIDEE, r.from(), r.to()).negate(),
                repository.countAmount(ACHAT, VALIDEE, r.from(), r.to()));
    }

    public List<TopProductRow> topProducts(Instant from, Instant to, int limit) {
        return repository.topProducts(VALIDEE, from, to, PageRequest.of(0, clamp(limit)));
    }

    public List<TopCustomerRow> topCustomers(Instant from, Instant to, int limit) {
        return repository.topCustomers(ACHAT, VALIDEE, from, to, PageRequest.of(0, clamp(limit)));
    }

    public List<UserActivityRow> userActivity(Instant from, Instant to) {
        return repository.userActivity(ACHAT, RECHARGE, VALIDEE, from, to);
    }

    public AgentDaySummary agentDay(Long userId, String username) {
        DashboardPeriods.Range r = DashboardPeriods.today(clock);
        return new AgentDaySummary(
                username,
                repository.userTransactionCount(userId, ACHAT, VALIDEE, r.from(), r.to()),
                repository.userSalesAmount(userId, ACHAT, VALIDEE, r.from(), r.to()),
                repository.userTransactionCount(userId, RECHARGE, VALIDEE, r.from(), r.to()),
                repository.userRechargeAmount(userId, RECHARGE, VALIDEE, r.from(), r.to()));
    }

    private static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), 50);
    }
}
