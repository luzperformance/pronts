package br.com.primeiroprontuario.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class CpfTest {

    @Test
    void formattedAndUnformattedValuesHaveTheSameCanonicalForm() {
        assertThat(Cpf.of("529.982.247-25")).isEqualTo(Cpf.of("52998224725"));
        assertThat(Cpf.of("529.982.247-25").value()).isEqualTo("52998224725");
    }

    @Test
    void invalidValuesAreRejected() {
        for (var invalidCpf : new String[] {"52998224724", "11111111111", "5299822472", ""}) {
            assertThatIllegalArgumentException().isThrownBy(() -> Cpf.of(invalidCpf));
        }
        assertThatIllegalArgumentException().isThrownBy(() -> Cpf.of(null));
    }
}
