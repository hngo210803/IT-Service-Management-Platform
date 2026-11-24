# Déployer IT Service Desk sur Kubernetes en local

Les manifestes réutilisent les images Docker du frontend et du backend construites avec Compose. Le frontend et le backend ont chacun un Deployment et un Service interne. PostgreSQL tourne dans un StatefulSet avec un volume de 1 Gio. Ce déploiement permet de gérer les tickets et de tester l’endpoint MCP. Pour essayer l’assistant IA, lancez l’application avec Docker Compose et Ollama.

Il faut avoir Docker Desktop, `kubectl` et [kind](https://kind.sigs.k8s.io/docs/user/quick-start/). Utilisez une version de `kubectl` qui diffère d’au plus une version mineure de celle du cluster. Depuis la racine du dépôt, créez le cluster, chargez les images et déployez l’application.

```sh
docker compose build backend frontend
docker compose pull postgres
kind create cluster --name itsm

# Exporter les images pour l’architecture de la machine.
itsm_image_dir=$(mktemp -d)
itsm_platform=$(docker image inspect --format '{{.Os}}/{{.Architecture}}' itsm-backend:local)
docker image save --platform "$itsm_platform" -o "$itsm_image_dir/images.tar" itsm-backend:local itsm-frontend:local postgres:17-alpine
kind load image-archive "$itsm_image_dir/images.tar" --name itsm
rm "$itsm_image_dir/images.tar"
rmdir "$itsm_image_dir"
kubectl --context kind-itsm apply -f k8s/namespace.yaml
kubectl --context kind-itsm -n itsm create secret generic itsm-db --from-literal=password="$(openssl rand -hex 24)"
kubectl --context kind-itsm apply -k k8s
kubectl --context kind-itsm -n itsm rollout status statefulset/postgres --timeout=180s
kubectl --context kind-itsm -n itsm rollout status deployment/backend --timeout=180s
kubectl --context kind-itsm -n itsm rollout status deployment/frontend --timeout=180s
```

Créez le Secret une seule fois par cluster. Après une modification des manifestes, relancez `kubectl --context kind-itsm apply -k k8s`. Si vous reconstruisez une image avec le tag `:local`, rechargez-la dans kind, puis redémarrez les Deployments avec `kubectl --context kind-itsm -n itsm rollout restart deployment/backend deployment/frontend`.

Ouvrez deux terminaux et laissez tourner une commande dans chacun.

```sh
# Terminal 1
kubectl --context kind-itsm -n itsm port-forward svc/frontend 9081:80

# Terminal 2
kubectl --context kind-itsm -n itsm port-forward svc/backend 9082:8080
```

Ouvrez [l’interface](http://127.0.0.1:9081) ou lancez les vérifications suivantes depuis un troisième terminal.

```sh
curl -fsS http://127.0.0.1:9082/actuator/health/liveness
curl -fsS http://127.0.0.1:9082/actuator/health/readiness
python3 scripts/smoke.py --base-url http://127.0.0.1:9081 --mcp-url http://127.0.0.1:9082/mcp
```

La sonde de démarrage laisse à Spring Boot le temps de se lancer. La sonde de vie vérifie que l’application tourne. La sonde de disponibilité vérifie aussi la connexion à PostgreSQL. Nginx expose `/healthz`. Les commandes ci-dessous permettent de consulter l’état des pods et les logs du backend et du frontend. [Documentation Kubernetes sur les sondes](https://kubernetes.io/docs/concepts/configuration/liveness-readiness-startup-probes/)

```sh
kubectl --context kind-itsm -n itsm get pods,pvc
kubectl --context kind-itsm -n itsm logs deployment/backend --tail=50 -f
kubectl --context kind-itsm -n itsm logs deployment/frontend --tail=50
kubectl --context kind-itsm -n itsm describe pods -l app=backend
```

## Tester la sonde de disponibilité

Pour lancer le test automatiquement, créez au moins un ticket dans le cluster local, puis exécutez la commande suivante à la racine du dépôt. Le script utilise Python 3 et le contexte `kind-itsm`. Il arrête brièvement PostgreSQL et le relance, même si une vérification échoue.

```bash
bash scripts/test-k8s-readiness.sh
```

Pour observer les étapes une par une, suivez les commandes ci-dessous.

Gardez les deux commandes de port-forward ouvertes. Créez un ticket sur [http://localhost:9081](http://localhost:9081) et notez son numéro.


Spring Boot vérifie que le backend est prêt à recevoir des requêtes et qu’il peut se connecter à PostgreSQL. Dans un troisième terminal, vérifiez d’abord que la sonde renvoie `200 UP`.

```bash
curl -si http://localhost:9082/actuator/health/readiness
```

Sur le cluster local de test, arrêtez temporairement PostgreSQL en ramenant le nombre de réplicas à zéro.

```bash
kubectl --context kind-itsm -n itsm scale statefulset/postgres --replicas=0
kubectl --context kind-itsm -n itsm wait --for=condition=Ready=false \
  pod -l app=backend --timeout=120s
kubectl --context kind-itsm -n itsm get pods -l app=backend
curl -si http://localhost:9082/actuator/health/readiness
curl -si http://localhost:9082/actuator/health/liveness
```

Le pod du backend reste `Running`, avec `0/1` dans la colonne `READY`. La sonde de disponibilité renvoie `503 DOWN` et le Service cesse d’envoyer de nouvelles requêtes à ce pod. La sonde de vie renvoie toujours `200 UP`, car Spring Boot continue de tourner.

Relancez PostgreSQL et vérifiez que le backend redevient disponible.

```bash
kubectl --context kind-itsm -n itsm scale statefulset/postgres --replicas=1
kubectl --context kind-itsm -n itsm rollout status statefulset/postgres --timeout=120s
kubectl --context kind-itsm -n itsm wait --for=condition=Ready \
  pod -l app=backend --timeout=120s
curl -si http://localhost:9082/actuator/health/readiness
```

La sonde doit de nouveau renvoyer `200 UP`. Après avoir actualisé l’interface, vous devriez retrouver le ticket créé au début du test. Le pod PostgreSQL retrouve les données sur son volume persistant.

Pour consulter les logs du backend ou les événements du pod, utilisez ces commandes.

```bash
kubectl --context kind-itsm -n itsm logs deployment/backend -c backend --tail=50
kubectl --context kind-itsm -n itsm describe pods -l app=backend
```
