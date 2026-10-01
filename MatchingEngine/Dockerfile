# ---- Build stage ----
FROM ubuntu:22.04 AS cppkafka-build

ENV DEBIAN_FRONTEND=noninteractive

# Install build tools and dependencies
RUN apt-get update --fix-missing && \
    apt-get install -y \
        build-essential \
        cmake \
        git \
        libboost-all-dev \
        librdkafka-dev \
        libssl-dev \
        pkg-config && \
    rm -rf /var/lib/apt/lists/*

# Build cppkafka from source
WORKDIR /tmp
RUN git clone --branch v0.4.1 https://github.com/mfontanini/cppkafka.git && \
    cd cppkafka && \
    mkdir build && cd build && \
    cmake .. && \
    make -j$(nproc) && \
    make install

# Copy built cppkafka source to /app/cppkafka for CMake add_subdirectory
RUN mkdir -p /app && cp -r /tmp/cppkafka /app/cppkafka

WORKDIR /app
# Ensure json.hpp is present before building
ADD https://raw.githubusercontent.com/nlohmann/json/v3.11.3/single_include/nlohmann/json.hpp /usr/include/nlohmann/json.hpp
COPY . .

RUN cmake -Bbuild -H. && cmake --build build --target MatchingEngine -- -j$(nproc)
RUN cmake --build build --target MatchingEngineTests -- -j$(nproc) && ctest --test-dir build --output-on-failure

# ---- Runtime stage ----
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# Install runtime dependencies
RUN apt-get update --fix-missing && \
    apt-get install -y \
        libboost-system1.74.0 \
        librdkafka1 \
        libssl3 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=cppkafka-build /app/build/MatchingEngine /app/MatchingEngine
COPY --from=cppkafka-build /usr/local/lib/libcppkafka.so.0.4.1 /usr/lib/
COPY --from=cppkafka-build /usr/local/lib/libcppkafka.so /usr/lib/
RUN cd /usr/lib && \
    [ -e libcppkafka.so.0 ] || ln -s libcppkafka.so.0.4.1 libcppkafka.so.0 && \
    [ -e libcppkafka.so ] || ln -s libcppkafka.so.0 libcppkafka.so && \
    ldconfig
ENV LD_LIBRARY_PATH=/usr/lib
CMD ["./MatchingEngine"]
