package com.stockflow.transfer;

import static com.stockflow.support.TestEntities.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.stockflow.common.BusinessRuleViolationException;
import com.stockflow.common.LineItemRequest;
import com.stockflow.common.NotFoundException;
import com.stockflow.inventory.InventoryService;
import com.stockflow.inventory.MovementType;
import com.stockflow.product.Product;
import com.stockflow.product.ProductRepository;
import com.stockflow.store.Store;
import com.stockflow.store.StoreRepository;
import com.stockflow.support.TestEntities;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final long TRANSFER_ID = 7L;

    @Mock
    private TransferRepository transfers;
    @Mock
    private StoreRepository stores;
    @Mock
    private ProductRepository products;
    @Mock
    private InventoryService inventory;

    private TransferService service;

    private final Store source = TestEntities.store("ST-SRC");
    private final Store destination = TestEntities.store("ST-DST");
    private final Product mugs = TestEntities.product("MUG-01");
    private final Product candles = TestEntities.product("CANDLE-01");

    @BeforeEach
    void setUp() {
        service = new TransferService(transfers, stores, products, inventory, TestEntities.CLOCK);
    }

    @Test
    void createBuildsARequestedTransferWithAllLines() {
        when(stores.requireByCode("ST-SRC")).thenReturn(source);
        when(stores.requireByCode("ST-DST")).thenReturn(destination);
        when(products.requireBySku("MUG-01")).thenReturn(mugs);
        when(products.requireBySku("CANDLE-01")).thenReturn(candles);
        when(transfers.save(any(Transfer.class)))
                .thenAnswer(call -> TestEntities.withId(call.getArgument(0), TRANSFER_ID));

        TransferResponse response = service.create(new CreateTransferRequest("ST-SRC", "ST-DST",
                List.of(new LineItemRequest("MUG-01", 10), new LineItemRequest("CANDLE-01", 4))));

        assertThat(response.id()).isEqualTo(TRANSFER_ID);
        assertThat(response.status()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.lines()).extracting(TransferLineResponse::sku, TransferLineResponse::quantity)
                .containsExactly(tuple("MUG-01", 10), tuple("CANDLE-01", 4));
    }

    @Test
    void createWithUnknownProductSavesNothing() {
        when(stores.requireByCode("ST-SRC")).thenReturn(source);
        when(stores.requireByCode("ST-DST")).thenReturn(destination);
        when(products.requireBySku("NOPE-01")).thenThrow(new NotFoundException("Product", "NOPE-01"));

        assertThatThrownBy(() -> service.create(new CreateTransferRequest("ST-SRC", "ST-DST",
                List.of(new LineItemRequest("NOPE-01", 1)))))
                .isInstanceOf(NotFoundException.class);
        verify(transfers, never()).save(any());
    }

    @Test
    void dispatchRemovesEveryLineFromTheSourceStore() {
        Transfer transfer = requestedTransfer();
        when(transfers.findDetailedById(TRANSFER_ID)).thenReturn(Optional.of(transfer));

        TransferResponse response = service.dispatch(TRANSFER_ID);

        assertThat(response.status()).isEqualTo(TransferStatus.DISPATCHED);
        assertThat(response.dispatchedAt()).isEqualTo(NOW);
        verify(inventory).removeStock(source, mugs, 10, MovementType.TRANSFER_OUT, "TRF-7");
        verify(inventory).removeStock(source, candles, 4, MovementType.TRANSFER_OUT, "TRF-7");
        verifyNoMoreInteractions(inventory);
    }

    @Test
    void dispatchingTwiceNeverTouchesStockAgain() {
        Transfer transfer = requestedTransfer();
        transfer.dispatch(NOW);
        when(transfers.findDetailedById(TRANSFER_ID)).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.dispatch(TRANSFER_ID)).isInstanceOf(InvalidTransferStateException.class);
        verifyNoMoreInteractions(inventory);
    }

    @Test
    void receiveBooksOnlyWhatArrived() {
        Transfer transfer = requestedTransfer();
        transfer.dispatch(NOW);
        when(transfers.findDetailedById(TRANSFER_ID)).thenReturn(Optional.of(transfer));

        // Mugs are not mentioned, so they arrived in full. Every candle was lost on the way.
        TransferResponse response = service.receive(TRANSFER_ID,
                new ReceiveTransferRequest(List.of(new ReceivedLineRequest("CANDLE-01", 0))));

        assertThat(response.status()).isEqualTo(TransferStatus.RECEIVED_WITH_DISCREPANCY);
        verify(inventory).addStock(destination, mugs, 10, MovementType.TRANSFER_IN, "TRF-7");
        verify(inventory, never()).addStock(any(), eq(candles), anyInt(), any(), any());
    }

    @Test
    void receiveWithoutBodyMeansEverythingArrived() {
        Transfer transfer = requestedTransfer();
        transfer.dispatch(NOW);
        when(transfers.findDetailedById(TRANSFER_ID)).thenReturn(Optional.of(transfer));

        TransferResponse response = service.receive(TRANSFER_ID, null);

        assertThat(response.status()).isEqualTo(TransferStatus.RECEIVED);
        verify(inventory).addStock(destination, mugs, 10, MovementType.TRANSFER_IN, "TRF-7");
        verify(inventory).addStock(destination, candles, 4, MovementType.TRANSFER_IN, "TRF-7");
    }

    @Test
    void receiveRejectsTheSameSkuTwiceBeforeLoadingTheTransfer() {
        ReceiveTransferRequest request = new ReceiveTransferRequest(List.of(
                new ReceivedLineRequest("MUG-01", 1), new ReceivedLineRequest("MUG-01", 2)));

        assertThatThrownBy(() -> service.receive(TRANSFER_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoMoreInteractions(transfers, inventory);
    }

    @Test
    void unknownTransferIsNotFound() {
        when(transfers.findDetailedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    private Transfer requestedTransfer() {
        Transfer transfer = TestEntities.withId(new Transfer(source, destination, NOW), TRANSFER_ID);
        transfer.addLine(mugs, 10);
        transfer.addLine(candles, 4);
        return transfer;
    }
}
