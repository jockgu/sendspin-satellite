#include <array>
#include <cassert>

#include "pcm_render_fifo.h"

namespace {

void renders_pcm_in_order() {
    sendspin::PcmRenderFifo fifo;
    const std::array<int16_t, 8> input{1, 2, 3, 4, 5, 6, 7, 8};
    std::array<int16_t, 8> output{};

    assert(fifo.write(input.data(), 4) == 4);
    const auto result = fifo.pull(output.data(), 4);
    assert(!result.underrun);
    assert(output == input);
}

void clear_never_renders_old_generation() {
    sendspin::PcmRenderFifo fifo;
    const std::array<int16_t, 4> old_audio{1, 1, 1, 1};
    const std::array<int16_t, 4> new_audio{2, 2, 2, 2};
    std::array<int16_t, 4> output{};

    assert(fifo.write(old_audio.data(), 2) == 2);
    fifo.clear();
    assert(fifo.write(new_audio.data(), 2) == 2);
    const auto result = fifo.pull(output.data(), 2);
    assert(!result.underrun);
    assert(output == new_audio);
}

void fifo_is_bounded_and_silences_underruns() {
    sendspin::PcmRenderFifo fifo;
    std::array<int16_t, sendspin::PcmRenderFifo::kBlockFrames * 2> block{};
    for (uint32_t index = 0; index < sendspin::PcmRenderFifo::kBlockCount; ++index) {
        assert(fifo.write(block.data(), sendspin::PcmRenderFifo::kBlockFrames) ==
            sendspin::PcmRenderFifo::kBlockFrames);
    }
    assert(fifo.write(block.data(), sendspin::PcmRenderFifo::kBlockFrames) == 0);

    sendspin::PcmRenderFifo empty;
    std::array<int16_t, 4> output{1, 1, 1, 1};
    const auto result = empty.pull(output.data(), 2);
    assert(result.underrun);
    assert((output == std::array<int16_t, 4>{}));
}

}  // namespace

int main() {
    renders_pcm_in_order();
    clear_never_renders_old_generation();
    fifo_is_bounded_and_silences_underruns();
}
