# IT Service Desk

IT Service Desk est une petite application de gestion de tickets informatiques. On peut créer un ticket, le retrouver dans la liste et changer son statut. J’ai réalisé ce projet pour apprendre à développer une application web full-stack, et à la faire tourner avec Docker Compose.

## Lancer le projet

Il faut avoir Docker et Docker Compose. Si vous utilisez Docker Desktop, ouvrez-le avant de lancer la commande suivante à la racine du dépôt.

```bash
docker compose up --build
```

Une fois les services démarrés, ouvrez [http://localhost:8081](http://localhost:8081).

Vous pouvez créer un ticket pour signaler un problème, par exemple une connexion VPN qui ne fonctionne pas ou un mot de passe oublié. Le ticket apparaît avec le statut **Ouvert**. Pour vérifier que le statut est bien enregistré dans PostgreSQL, passez-le à **En cours**, puis actualisez la page.

Pour arrêter l’application, faites `Ctrl+C` dans le terminal. Les tickets restent dans le volume PostgreSQL et seront encore là au prochain démarrage.

## Comment l’application fonctionne

- **Angular** affiche les tickets et envoie les requêtes à l’API via Nginx.
- **Spring Boot** gère la création des tickets et la mise à jour de leur statut.
- **Spring Data JPA** enregistre les données dans PostgreSQL.

## L’assistant IA

Ollama est nécessaire pour utiliser l’assistant. La gestion des tickets fonctionne aussi lorsque Ollama est arrêté.

### Démarrer Ollama

**macOS et Windows avec Docker Desktop.** Ouvrez Ollama sur votre machine, puis téléchargez les deux modèles dans un terminal. Le backend se connecte à Ollama via `host.docker.internal:11434`. [Télécharger Ollama](https://ollama.com/download) · [Réseau Docker Desktop](https://docs.docker.com/desktop/features/networking/#i-want-to-connect-from-a-container-to-a-service-on-the-host)

```bash
ollama pull qwen3:4b
ollama pull bge-m3
```

**Linux avec Docker Engine.** Après avoir démarré l’application, vous pouvez lancer Ollama dans un conteneur sur le même réseau Docker. [Documentation Docker d’Ollama](https://docs.ollama.com/docker)

```bash
docker run -d --name itsm-ollama --network itsm-platform_default \
  -v itsm-ollama-models:/root/.ollama ollama/ollama
docker exec itsm-ollama ollama pull qwen3:4b
docker exec itsm-ollama ollama pull bge-m3
OLLAMA_URL=http://itsm-ollama:11434 docker compose up -d backend
docker compose restart frontend
```

### Que peut-on demander ?

**Suivi d’un ticket.** « Quel est le statut du ticket #numéro d’un ticket existant ? »

**Exemples de questions couvertes par la documentation de démonstration**

- « Comment me connecter au VPN ? »
- « Comment réinitialiser mon mot de passe ? »
- « Comment signaler un incident informatique ? »

Pour ces questions, l’assistant cherche un passage dans les guides du projet et s’en sert pour formuler sa réponse. Les passages cités sont accessibles dans **Sources citées**.

## Lancer un test rapide (facultatif)

Avec l’application démarrée, lancez le script suivant depuis la racine du dépôt. Il faut avoir Python 3.

```bash
python3 scripts/smoke.py \
  --base-url http://127.0.0.1:8081 \
  --mcp-url http://127.0.0.1:8082/mcp
```

Le script crée un ticket de test, change son statut et vérifie que l’outil MCP renvoie le nouveau statut. GitHub Actions construit les deux images Docker et lance ce test à chaque push et pull request.
