package com.doroddi.courseregistration.common.api;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageResponseTest {
    @Test
    void mapsContentAndPageMetadata() {
        Page<String> page = new PageImpl<>(List.of("first", "second"), PageRequest.of(0, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("first", "second");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void preservesZeroTotalsWhenNoElementsMatch() {
        Page<String> page = Page.empty(PageRequest.of(0, 20));

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void preservesRequestedPageAndActualTotalsForAnOutOfRangePage() {
        Page<String> page = new PageImpl<>(List.of(), PageRequest.of(3, 20), 53);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).isEmpty();
        assertThat(response.page()).isEqualTo(3);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(53);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void preservesConfiguredSizeOnAPartialLastPage() {
        List<Integer> content = IntStream.rangeClosed(41, 53).boxed().toList();
        Page<Integer> page = new PageImpl<>(content, PageRequest.of(2, 20), 53);

        PageResponse<Integer> response = PageResponse.from(page);

        assertThat(response.content()).containsExactlyElementsOf(content).hasSize(13);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(53);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void copiesContentAndPreventsMutationThroughTheResponse() {
        List<String> original = new ArrayList<>(List.of("first"));
        PageResponse<String> response = new PageResponse<>(original, 0, 20, 1, 1);

        original.add("second");

        assertThat(response.content()).containsExactly("first");
        assertThatThrownBy(() -> response.content().add("third"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void serializesExactlyTheFiveApiFields() {
        JsonMapper mapper = JsonMapper.builder().build();
        Page<String> page = new PageImpl<>(List.of("student-1"), PageRequest.of(0, 20), 1);

        String json = mapper.writeValueAsString(PageResponse.from(page));

        assertThat(mapper.readTree(json)).isEqualTo(mapper.readTree("""
                {
                  "content": ["student-1"],
                  "page": 0,
                  "size": 20,
                  "totalElements": 1,
                  "totalPages": 1
                }
                """));
    }
}
