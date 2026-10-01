package com.stockflow.product;

import static com.stockflow.support.TestEntities.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockflow.common.ConflictException;
import com.stockflow.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository products;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, TestEntities.CLOCK);
    }

    @Test
    void createStoresTrimmedNameAndCreationTime() {
        when(products.existsBySku("MUG-01")).thenReturn(false);
        when(products.save(any(Product.class))).thenAnswer(call -> TestEntities.withId(call.getArgument(0), 7L));

        ProductResponse response = service.create(new CreateProductRequest("MUG-01", "  Ceramic mug  "));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.sku()).isEqualTo("MUG-01");
        assertThat(response.name()).isEqualTo("Ceramic mug");
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void duplicateSkuIsRejectedBeforeSaving() {
        when(products.existsBySku("MUG-01")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateProductRequest("MUG-01", "Ceramic mug")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("MUG-01");
        verify(products, never()).save(any());
    }
}
