import mitt from 'mitt'

// Vue 3 事件总线（替代 Vue 2 的 new Vue() 事件总线）
const eventBus = mitt()

export default eventBus
