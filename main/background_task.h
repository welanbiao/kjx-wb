#ifndef BACKGROUND_TASK_H
#define BACKGROUND_TASK_H

#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <mutex>
#include <list>
#include <condition_variable>
#include <atomic>
#include <functional>

class BackgroundTask {
public:
    BackgroundTask(uint32_t stack_size = 4096 * 2);
    ~BackgroundTask();

    // Returns false if the queue is full and the task was dropped
    bool Schedule(std::function<void()> callback);
    void WaitForCompletion();
    size_t GetActiveTaskCount() const { return active_tasks_.load(); }

private:
    std::mutex mutex_;
    std::list<std::function<void()>> main_tasks_;
    std::condition_variable condition_variable_;
    TaskHandle_t background_task_handle_ = nullptr;
    std::atomic<size_t> active_tasks_{0};

    void BackgroundTaskLoop();
};

#endif
