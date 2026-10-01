#include "kafka/OrderMessageParser.hpp"
#include <cmath>
#include <nlohmann/json.hpp>
#include <stdexcept>

namespace kafka
{
    namespace
    {
        std::string requiredId(const nlohmann::json &j, const char *field)
        {
            auto value = j.at(field).get<std::string>();
            if (value.empty())
                throw std::invalid_argument(std::string("Empty ") + field);
            return value;
        }

        dto::OrderMessage parseValidated(const std::string &payload)
        {
            if (payload.empty())
                throw std::invalid_argument("Empty order message");
            const nlohmann::json j = nlohmann::json::parse(payload);
            if (!j.is_object())
                throw std::invalid_argument("Order message is not a JSON object");

            dto::OrderMessage message;
            message.order.order_id = requiredId(j, "orderId");
            message.order.user_id = requiredId(j, "userId");

            const std::string action = j.contains("action") ? j.at("action").get<std::string>() : "NEW";
            if (action == "CANCEL")
            {
                message.action = dto::OrderAction::CANCEL;
                message.order.quantity = 0;
                message.order.price = 0.0;
                message.order.side = dto::Side::BUY;
                message.order.type = dto::OrderType::LIMIT;
                return message;
            }
            if (action != "NEW")
                throw std::invalid_argument("Invalid action: " + action);

            message.order.quantity = j.at("quantity").get<int>();
            message.order.price = j.at("price").get<double>();
            if (message.order.quantity <= 0 || !std::isfinite(message.order.price) || message.order.price <= 0)
                throw std::invalid_argument("Invalid quantity or price");

            const auto side = j.at("side").get<std::string>();
            if (side == "BUY")
                message.order.side = dto::Side::BUY;
            else if (side == "SELL")
                message.order.side = dto::Side::SELL;
            else
                throw std::invalid_argument("Invalid side: " + side);

            const auto type = j.at("type").get<std::string>();
            if (type == "MARKET")
                message.order.type = dto::OrderType::MARKET;
            else if (type == "LIMIT")
                message.order.type = dto::OrderType::LIMIT;
            else
                throw std::invalid_argument("Invalid order type: " + type);
            return message;
        }
    } // namespace

    dto::OrderMessage parseOrderMessage(const std::string &payload)
    {
        try
        {
            return parseValidated(payload);
        }
        catch (const nlohmann::json::exception &e)
        {
            // Missing fields, wrong types and broken JSON are all bad input, not infrastructure failures.
            throw std::invalid_argument(e.what());
        }
    }

} // namespace kafka
