#include "pcm_render_fifo.h"

#include <algorithm>
#include <cstring>

namespace sendspin {

uint32_t PcmRenderFifo::write(const int16_t* samples, const uint32_t frames) {
    uint32_t written = 0;
    const uint32_t generation = generation_.load(std::memory_order_acquire);
    while (written < frames) {
        const uint32_t write_block = write_block_.load(std::memory_order_relaxed);
        const uint32_t read_block = read_block_.load(std::memory_order_acquire);
        if (write_block - read_block >= kBlockCount) break;

        Block& block = blocks_[write_block % kBlockCount];
        const uint32_t block_frames = std::min(kBlockFrames, frames - written);
        std::memcpy(
            block.samples.data(),
            samples + static_cast<size_t>(written) * kChannels,
            static_cast<size_t>(block_frames) * kChannels * sizeof(int16_t));
        block.frames = block_frames;
        block.generation = generation;
        write_block_.store(write_block + 1, std::memory_order_release);
        written += block_frames;
    }
    return written;
}

PcmRenderFifo::PullResult PcmRenderFifo::pull(int16_t* output, const uint32_t frames) {
    uint32_t copied = 0;
    const uint32_t generation = generation_.load(std::memory_order_acquire);
    while (copied < frames) {
        uint32_t read_block = read_block_.load(std::memory_order_relaxed);
        const uint32_t write_block = write_block_.load(std::memory_order_acquire);
        if (read_block == write_block) break;

        Block& block = blocks_[read_block % kBlockCount];
        if (block.generation != generation) {
            consumer_offset_.store(0, std::memory_order_relaxed);
            read_block_.store(read_block + 1, std::memory_order_release);
            continue;
        }

        const uint32_t consumer_offset = consumer_offset_.load(std::memory_order_relaxed);
        const uint32_t block_available = block.frames - consumer_offset;
        const uint32_t take = std::min(block_available, frames - copied);
        std::memcpy(
            output + static_cast<size_t>(copied) * kChannels,
            block.samples.data() + static_cast<size_t>(consumer_offset) * kChannels,
            static_cast<size_t>(take) * kChannels * sizeof(int16_t));
        copied += take;
        const uint32_t new_offset = consumer_offset + take;
        consumer_offset_.store(new_offset, std::memory_order_relaxed);
        if (new_offset == block.frames) {
            consumer_offset_.store(0, std::memory_order_relaxed);
            read_block_.store(read_block + 1, std::memory_order_release);
        }
    }
    if (copied < frames) {
        std::memset(
            output + static_cast<size_t>(copied) * kChannels,
            0,
            static_cast<size_t>(frames - copied) * kChannels * sizeof(int16_t));
    }
    return {copied, copied != frames};
}

void PcmRenderFifo::clear() {
    generation_.fetch_add(1, std::memory_order_acq_rel);
}

uint32_t PcmRenderFifo::queued_frames() const {
    const uint32_t write_block = write_block_.load(std::memory_order_acquire);
    const uint32_t read_block = read_block_.load(std::memory_order_acquire);
    if (write_block == read_block) return 0;

    const uint32_t generation = generation_.load(std::memory_order_acquire);
    uint32_t frames = 0;
    for (uint32_t block = read_block; block != write_block; ++block) {
        const Block& entry = blocks_[block % kBlockCount];
        if (entry.generation == generation) frames += entry.frames;
    }
    if (blocks_[read_block % kBlockCount].generation != generation) return frames;
    return frames - consumer_offset_.load(std::memory_order_relaxed);
}

}  // namespace sendspin
