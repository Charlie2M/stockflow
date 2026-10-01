package com.stockflow.transfer;

import static com.stockflow.support.TestEntities.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockflow.common.BusinessRuleViolationException;
import com.stockflow.product.Product;
import com.stockflow.support.TestEntities;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransferTest {

    private static final Instant DISPATCHED_AT = NOW.plusSeconds(3600);
    private static final Instant RECEIVED_AT = NOW.plusSeconds(7200);

    private final Product mugs = TestEntities.product("MUG-01");
    private final Product candles = TestEntities.product("CANDLE-01");

    @Test
    void newTransferIsRequested() {
        Transfer transfer = transferWithLines();

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(transfer.getCreatedAt()).isEqualTo(NOW);
        assertThat(transfer.getLines()).extracting(TransferLine::getSku).containsExactly("MUG-01", "CANDLE-01");
    }

    @Test
    void sourceAndDestinationMustDiffer() {
        assertThatThrownBy(() -> new Transfer(TestEntities.store("ST-SAME"), TestEntities.store("ST-SAME"), NOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("must be different");
    }

    @Test
    void sameProductCannotBeListedTwice() {
        Transfer transfer = transferWithLines();

        assertThatThrownBy(() -> transfer.addLine(mugs, 1))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("MUG-01");
    }

    @Test
    void lineQuantityMustBePositive() {
        Transfer transfer = emptyTransfer();

        assertThatThrownBy(() -> transfer.addLine(mugs, 0)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void dispatchMarksTransferAsShipped() {
        Transfer transfer = transferWithLines();

        transfer.dispatch(DISPATCHED_AT);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.DISPATCHED);
        assertThat(transfer.getDispatchedAt()).isEqualTo(DISPATCHED_AT);
        assertThat(transfer.getCompletedAt()).isNull();
    }

    @Test
    void emptyTransferCannotBeDispatched() {
        Transfer transfer = emptyTransfer();

        assertThatThrownBy(() -> transfer.dispatch(DISPATCHED_AT)).isInstanceOf(BusinessRuleViolationException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REQUESTED);
    }

    @Test
    void linesCannotBeAddedAfterDispatch() {
        Transfer transfer = transferWithLines();
        transfer.dispatch(DISPATCHED_AT);

        assertThatThrownBy(() -> transfer.addLine(TestEntities.product("LAMP-01"), 1))
                .isInstanceOf(InvalidTransferStateException.class);
    }

    @Test
    void receivingEverythingCompletesTheTransfer() {
        Transfer transfer = dispatchedTransfer();

        transfer.receive(Map.of(), RECEIVED_AT);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.RECEIVED);
        assertThat(transfer.getCompletedAt()).isEqualTo(RECEIVED_AT);
        assertThat(transfer.getLines()).extracting(TransferLine::getDiscrepancy).containsExactly(0, 0);
    }

    @Test
    void shortDeliveryIsRecordedAsDiscrepancy() {
        Transfer transfer = dispatchedTransfer();

        transfer.receive(Map.of("MUG-01", 7), RECEIVED_AT);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.RECEIVED_WITH_DISCREPANCY);
        assertThat(transfer.getLines()).extracting(TransferLine::getReceivedQuantity).containsExactly(7, 4);
        assertThat(transfer.getLines()).extracting(TransferLine::getDiscrepancy).containsExactly(3, 0);
    }

    @Test
    void receivingMoreThanWasShippedIsRejectedWithoutPartialUpdates() {
        Transfer transfer = dispatchedTransfer();

        // The first line is valid, the second is not: nothing at all may be recorded.
        assertThatThrownBy(() -> transfer.receive(Map.of("MUG-01", 5, "CANDLE-01", 5), RECEIVED_AT))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("CANDLE-01");

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.DISPATCHED);
        assertThat(transfer.getLines()).extracting(TransferLine::getReceivedQuantity).containsOnlyNulls();
    }

    @Test
    void receivingAProductThatWasNotShippedIsRejected() {
        Transfer transfer = dispatchedTransfer();

        assertThatThrownBy(() -> transfer.receive(Map.of("LAMP-01", 1), RECEIVED_AT))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("LAMP-01");
    }

    @Test
    void cannotReceiveBeforeDispatch() {
        Transfer transfer = transferWithLines();

        assertThatThrownBy(() -> transfer.receive(Map.of(), RECEIVED_AT))
                .isInstanceOfSatisfying(InvalidTransferStateException.class,
                        ex -> assertThat(ex.getCurrentStatus()).isEqualTo(TransferStatus.REQUESTED));
    }

    @Test
    void requestedTransferCanBeCancelled() {
        Transfer transfer = transferWithLines();

        transfer.cancel(DISPATCHED_AT);

        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(transfer.getCompletedAt()).isEqualTo(DISPATCHED_AT);
    }

    @Test
    void goodsOnTheRoadCannotBeCancelled() {
        Transfer transfer = dispatchedTransfer();

        assertThatThrownBy(() -> transfer.cancel(RECEIVED_AT)).isInstanceOf(InvalidTransferStateException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.DISPATCHED);
    }

    @ParameterizedTest
    @EnumSource(value = TransferStatus.class, names = {"RECEIVED", "RECEIVED_WITH_DISCREPANCY", "CANCELLED"})
    void finishedTransfersAcceptNoFurtherTransitions(TransferStatus finalStatus) {
        Transfer transfer = transferIn(finalStatus);

        assertThatThrownBy(() -> transfer.dispatch(RECEIVED_AT)).isInstanceOf(InvalidTransferStateException.class);
        assertThatThrownBy(() -> transfer.receive(Map.of(), RECEIVED_AT))
                .isInstanceOf(InvalidTransferStateException.class);
        assertThatThrownBy(() -> transfer.cancel(RECEIVED_AT)).isInstanceOf(InvalidTransferStateException.class);
        assertThat(transfer.getStatus()).isEqualTo(finalStatus);
    }

    @Test
    void referenceLinksLedgerEntriesToTheTransfer() {
        Transfer transfer = TestEntities.withId(transferWithLines(), 42L);

        assertThat(transfer.getReference()).isEqualTo("TRF-42");
    }

    private Transfer emptyTransfer() {
        return new Transfer(TestEntities.store("ST-SRC"), TestEntities.store("ST-DST"), NOW);
    }

    private Transfer transferWithLines() {
        Transfer transfer = emptyTransfer();
        transfer.addLine(mugs, 10);
        transfer.addLine(candles, 4);
        return transfer;
    }

    private Transfer dispatchedTransfer() {
        Transfer transfer = transferWithLines();
        transfer.dispatch(DISPATCHED_AT);
        return transfer;
    }

    private Transfer transferIn(TransferStatus status) {
        Transfer transfer = transferWithLines();
        switch (status) {
            case CANCELLED -> transfer.cancel(DISPATCHED_AT);
            case RECEIVED -> {
                transfer.dispatch(DISPATCHED_AT);
                transfer.receive(Map.of(), RECEIVED_AT);
            }
            case RECEIVED_WITH_DISCREPANCY -> {
                transfer.dispatch(DISPATCHED_AT);
                transfer.receive(Map.of("MUG-01", 9), RECEIVED_AT);
            }
            default -> throw new IllegalArgumentException("Not a final status: " + status);
        }
        assertThat(transfer.getStatus()).isEqualTo(status);
        return transfer;
    }
}
