#include "kafka/KafkaOrderConsumer.hpp"
#include "service/OrderMatchingService.hpp"
#include <cstdlib>
#include <iostream>

int main()
{
    const char* statePath = std::getenv("MATCHER_STATE_FILE");
    if (!statePath || !*statePath) {
        std::cerr << "MATCHER_STATE_FILE is required" << std::endl;
        return 1;
    }
    core::OrderMatchingService service;
    kafka::KafkaOrderConsumer consumer;
    consumer.start(service);
    return 0;
}
