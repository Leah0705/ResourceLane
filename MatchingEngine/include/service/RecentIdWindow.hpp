#pragma once
#include <cstddef>
#include <deque>
#include <stdexcept>
#include <string>
#include <unordered_set>

namespace core
{
    /**
     * @class RecentIdWindow
     * @brief Remembers the most recent message IDs so a republished message is applied once.
     *
     * The oldest ID is evicted when the window is full, which keeps the persisted
     * matcher state bounded instead of growing with every order ever processed.
     */
    class RecentIdWindow
    {
    public:
        explicit RecentIdWindow(std::size_t capacity) : capacity_(capacity)
        {
            if (capacity_ == 0)
                throw std::invalid_argument("Dedup window must be positive");
        }

        /**
         * @brief Records an ID.
         * @return false when the ID is already in the window.
         */
        bool insert(const std::string &id)
        {
            if (!ids_.insert(id).second)
                return false;
            order_.push_back(id);
            while (order_.size() > capacity_)
            {
                ids_.erase(order_.front());
                order_.pop_front();
            }
            return true;
        }

        bool contains(const std::string &id) const { return ids_.count(id) > 0; }
        std::size_t size() const { return order_.size(); }
        std::size_t capacity() const { return capacity_; }
        /** @brief IDs from oldest to newest. */
        const std::deque<std::string> &ordered() const { return order_; }

        void clear()
        {
            ids_.clear();
            order_.clear();
        }

    private:
        std::size_t capacity_;
        std::deque<std::string> order_;
        std::unordered_set<std::string> ids_;
    };

} // namespace core
