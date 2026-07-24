# Spring Boot serve o frontend em produção

Em produção, o Vite gerará os artefatos estáticos do frontend e o próprio
Spring Boot os servirá. A aplicação continuará com uma única imagem, um único
pod e uma única origem HTTP, sem Nginx, serviço Kubernetes adicional ou
configuração de CORS.

Durante o desenvolvimento, o frontend continuará executando pelo servidor do
Vite em `frontend/`, com proxy para a API Spring Boot. O diretório de saída do
build é um artefato gerado e não substitui o código-fonte React.
