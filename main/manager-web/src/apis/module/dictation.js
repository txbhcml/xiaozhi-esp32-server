import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';

/**
 * 听写助手 API
 * 对应 Java 后端 /dict/task /dict/record /dict/vocabulary 路由
 */
export default {
    // ==================== 听写任务 ====================

    // 分页查询听写任务
    getTaskPage(params, callback) {
        const queryParams = new URLSearchParams({
            page: params.page || 1,
            limit: params.limit || 10,
            taskName: params.taskName || '',
            status: params.status === undefined || params.status === null ? '' : params.status
        }).toString();
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/dict/task/page?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取听写任务列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getTaskPage(params, callback)
                })
            }).send()
    },

    // 获取听写任务详情
    getTaskDetail(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/dict/task/${id}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取听写任务详情失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getTaskDetail(id, callback)
                })
            }).send()
    },

    // 创建/更新听写任务（id 为空时创建，非空时更新）
    saveTask(data, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/dict/task`)
            .method('POST')
            .data(data)
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('保存听写任务失败:', err)
                RequestService.reAjaxFun(() => {
                    this.saveTask(data, callback)
                })
            }).send()
    },

    // 删除听写任务
    deleteTask(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/dict/task/${id}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('删除听写任务失败:', err)
                RequestService.reAjaxFun(() => {
                    this.deleteTask(id, callback)
                })
            }).send()
    },

    // 更新听写任务状态
    updateTaskStatus(id, status, callback) {
        const queryParams = new URLSearchParams({ status }).toString();
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/dict/task/${id}/status?${queryParams}`)
            .method('PUT')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('更新听写任务状态失败:', err)
                RequestService.reAjaxFun(() => {
                    this.updateTaskStatus(id, status, callback)
                })
            }).send()
    },

    // ==================== 听写记录 ====================

    // 分页查询听写记录
    getRecordPage(params, callback) {
        const queryParams = new URLSearchParams({
            page: params.page || 1,
            limit: params.limit || 10,
            taskName: params.taskName || '',
            taskId: params.taskId || ''
        }).toString();
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/dict/record/page?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取听写记录列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getRecordPage(params, callback)
                })
            }).send()
    },

    // 获取听写记录详情
    getRecordDetail(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/dict/record/${id}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取听写记录详情失败:', err)
                RequestService.reAjaxFun(() => {
                    this.getRecordDetail(id, callback)
                })
            }).send()
    },

    // 删除听写记录
    deleteRecord(id, callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/dict/record/${id}`)
            .method('DELETE')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('删除听写记录失败:', err)
                RequestService.reAjaxFun(() => {
                    this.deleteRecord(id, callback)
                })
            }).send()
    },

    // ==================== 词汇查询 ====================

    // 列出所有词书
    listBooks(callback) {
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/dict/vocabulary/book/list`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('获取词书列表失败:', err)
                RequestService.reAjaxFun(() => {
                    this.listBooks(callback)
                })
            }).send()
    },

    // 分页查询词书内单词
    pageWordsByBook(params, callback) {
        const queryParams = new URLSearchParams({
            word: params.word || '',
            page: params.page || 1,
            limit: params.limit || 20
        }).toString();
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/dict/vocabulary/book/${params.bookId}/word/page?${queryParams}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('查询词书单词失败:', err)
                RequestService.reAjaxFun(() => {
                    this.pageWordsByBook(params, callback)
                })
            }).send()
    },

    // 按 ID 批量查询单词（用于回显已选单词）
    listWordsByIds(ids, callback) {
        const queryParams = new URLSearchParams();
        if (Array.isArray(ids)) {
            ids.forEach(id => queryParams.append('ids', id));
        }
        RequestService.sendRequest()
            .url(`${getServiceUrl()}/dict/vocabulary/word/list?${queryParams.toString()}`)
            .method('GET')
            .success((res) => {
                RequestService.clearRequestTime()
                callback(res)
            })
            .networkFail((err) => {
                console.error('批量查询单词失败:', err)
                RequestService.reAjaxFun(() => {
                    this.listWordsByIds(ids, callback)
                })
            }).send()
    }
}
