import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import LogView from './views/LogView.vue'
import ScreenView from './views/ScreenView.vue'
import './style.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: LogView },
    { path: '/scherm', component: ScreenView },
  ],
})

createApp(App).use(router).mount('#app')
