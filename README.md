# UVC Capture Lite TV

APK de teste para Android TV/projetor Android receber vídeo de uma placa HDMI→USB UVC com o mínimo de processamento possível.

## Objetivo desta versão
- sem gravação
- sem áudio USB
- sem filtros
- sem overlays pesados
- tela cheia
- controle remoto: OK mostra/oculta o painel de status
- tenta preferir 1280×720 MJPEG, próximo de 60 fps
- fallback para outro 720p e depois 640×480
- usa `com.herohan:UVCAndroid:1.0.13` (libusb/libuvc)

## Gerar o APK no GitHub
1. Envie TODO o conteúdo desta pasta para a raiz de um repositório GitHub.
2. Confira se existe `.github/workflows/build-apk.yml`.
3. Vá a **Actions** → **Build UVC Capture Lite TV APK** → **Run workflow**.
4. Quando terminar, baixe o artifact `UVCCaptureLiteTV-debug`.
5. Dentro dele estará `app-debug.apk`.

## Teste recomendado
1. Instale o APK no projetor.
2. Abra o app antes de conectar a placa, se possível.
3. Conecte a placa USB.
4. Aceite a permissão USB.
5. Confirme se o vídeo aparece e observe se o projetor permanece estável.

Se o projetor ainda reiniciar antes mesmo do preview ou imediatamente ao abrir a placa, teste um hub USB com fonte própria. Isso aponta para alimentação/driver USB do projetor, algo que um app sozinho pode não corrigir.

## Observação
A biblioteca UVC escolhe entre os modos anunciados pelo próprio dispositivo. Algumas placas anunciam combinações que o firmware do Android não consegue negociar; por isso esta versão trata falha e tenta manter a interface viva.


## Versão 0.2 — abertura automática para projetor/Android TV

- Não há botão para iniciar a captura.
- Ao abrir o app, ele procura automaticamente uma placa USB UVC já conectada.
- Se a placa for conectada depois, o app abre assim que o Android a detectar.
- A primeira conexão pode exibir o diálogo de permissão USB do próprio Android; confirme com o botão OK do controle.
- Depois de autorizado, o fluxo normal é apenas abrir o app e receber a imagem.
- Se a placa for removida e reconectada, o app tenta abrir novamente sem touch.
