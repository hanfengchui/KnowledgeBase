import { createApp } from 'vue'
import 'element-plus/dist/index.css'
import './styles.css'
import App from './App.vue'
import router from './router'
import { installElementPlus } from './plugins/element-plus'

const app = createApp(App)

installElementPlus(app)
app.use(router).mount('#app')
