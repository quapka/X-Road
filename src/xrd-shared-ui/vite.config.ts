import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import vuetify from 'vite-plugin-vuetify'
import dts from "vite-plugin-dts";
import { resolve } from "node:path";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    dts(),
    vueJsx(),
    vuetify({ autoImport: true})
  ],
  build: {
    lib: {
      entry: resolve(__dirname, "src/index.ts"),
      name: "XrdSharedUI",
      fileName: "xrd-shared-ui",
    },
    rollupOptions: {
      external: ["vue", "vuetify"],
      output: {
        globals: {
          vue: "Vue",
        },
      },
    }
  },
})
