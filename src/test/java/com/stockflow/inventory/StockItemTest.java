package com.stockflow.inventory;

import static com.stockflow.support.TestEntities.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stockflow.support.TestEntities;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StockItemTest {

    private static final Instant LATER = NOW.plusSeconds(60);

    private final StockItem item = new StockItem(TestEntities.store("ST-001"), TestEntities.product("SKU-001"), NOW);

    @Test
    void newItemStartsEmpty() {
        assertThat(item.getQuantity()).isZero();
        assertThat(item.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void increaseAddsUnitsAndTouchesTimestamp() {
        item.increase(10, LATER);

        assertThat(item.getQuantity()).isEqualTo(10);
        assertThat(item.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void decreaseCanEmptyTheShelfExactly() {
        item.increase(5, NOW);

        item.decrease(5, LATER);

        assertThat(item.getQuantity()).isZero();
    }

    @Test
    void decreaseBelowZeroIsRejectedAndChangesNothing() {
        item.increase(3, NOW);

        assertThatThrownBy(() -> item.decrease(4, LATER))
                .isInstanceOfSatisfying(InsufficientStockException.class, ex -> {
                    assertThat(ex.getRequested()).isEqualTo(4);
                    assertThat(ex.getAvailable()).isEqualTo(3);
                    assertThat(ex.getDetails()).containsEntry("storeCode", "ST-001").containsEntry("sku", "SKU-001");
                });
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void nonPositiveAmountsAreProgrammingErrors(int amount) {
        assertThatThrownBy(() -> item.increase(amount, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item.decrease(amount, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overflowFailsLoudlyInsteadOfWrappingToNegative() {
        item.increase(Integer.MAX_VALUE, NOW);

        assertThatThrownBy(() -> item.increase(1, LATER)).isInstanceOf(ArithmeticException.class);
        assertThat(item.getQuantity()).isEqualTo(Integer.MAX_VALUE);
    }
}
