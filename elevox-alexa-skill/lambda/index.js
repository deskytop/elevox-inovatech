import Alexa from 'ask-sdk-core';
import admin from 'firebase-admin';

// Inicializa Firebase Admin
// IMPORTANTE: Configure as credenciais do Firebase usando variáveis de ambiente
try {
  console.log('Iniciando inicialização do Firebase...');
  console.log('Project ID:', process.env.FIREBASE_PROJECT_ID);
  console.log('Client Email:', process.env.FIREBASE_CLIENT_EMAIL);
  console.log('Database URL:', process.env.FIREBASE_DATABASE_URL);
  console.log('Private Key exists:', !!process.env.FIREBASE_PRIVATE_KEY);

  const privateKey = process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n');
  console.log('Private Key length after replacement:', privateKey?.length);

  admin.initializeApp({
    credential: admin.credential.cert({
      projectId: process.env.FIREBASE_PROJECT_ID,
      clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
      privateKey: privateKey
    }),
    databaseURL: process.env.FIREBASE_DATABASE_URL
  });
  console.log('Firebase inicializado com sucesso');
} catch (error) {
  console.error('Erro ao inicializar Firebase:', error);
  console.error('Error stack:', error.stack);
}

const db = admin.database();

/**
 * Handler para quando o usuário abre a skill
 * "Alexa, abre Elevox"
 */
const LaunchRequestHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === 'LaunchRequest';
  },
  handle(handlerInput) {
    const speakOutput = 'Bem-vindo ao Elevox! Você pode me pedir para chamar o elevador ou ir para um andar específico. Como posso ajudar?';

    return handlerInput.responseBuilder
      .speak(speakOutput)
      .reprompt('Você pode dizer: chama o elevador, ou ir para o andar cinco. O que deseja fazer?')
      .getResponse();
  }
};

/**
 * Handler para chamar o elevador
 * "Alexa, pede Elevox para chamar o elevador no andar 3"
 */
const ChamarElevadorIntentHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
      && Alexa.getIntentName(handlerInput.requestEnvelope) === 'ChamarElevadorIntent';
  },
  async handle(handlerInput) {
    const slots = handlerInput.requestEnvelope.request.intent.slots;
    const andarSlot = slots.andar_atual;  // Na verdade é o andar de DESTINO

    // Se não forneceu o andar de destino, pede para tentar novamente
    if (!andarSlot || !andarSlot.value) {
      return handlerInput.responseBuilder
        .speak('Por favor, me diga para qual andar deseja chamar o elevador. Por exemplo: chama o elevador no térreo, ou chama o elevador no primeiro andar.')
        .reprompt('Diga: chama o elevador no térreo, primeiro, segundo ou terceiro andar.')
        .getResponse();
    }

    // Pega o ID do slot (que vem do tipo customizado ANDAR_TYPE)
    let destino;
    if (andarSlot.resolutions &&
        andarSlot.resolutions.resolutionsPerAuthority &&
        andarSlot.resolutions.resolutionsPerAuthority[0] &&
        andarSlot.resolutions.resolutionsPerAuthority[0].values &&
        andarSlot.resolutions.resolutionsPerAuthority[0].values[0]) {
      // Alexa reconheceu o slot - usa o ID (0, 1, 2 ou 3)
      destino = parseInt(andarSlot.resolutions.resolutionsPerAuthority[0].values[0].value.id);
    } else {
      // Fallback: tenta converter o valor direto
      destino = parseInt(andarSlot.value);
    }

    // Valida o andar de destino (térreo, 1º, 2º ou 3º)
    if (isNaN(destino) || destino < 0 || destino > 3) {
      return handlerInput.responseBuilder
        .speak('Desculpe, não entendi o andar. Por favor, diga térreo, primeiro, segundo ou terceiro andar.')
        .getResponse();
    }

    // Formata o nome do andar baseado no ID
    let andarNome = 'térreo';
    if (destino === 0) {
      andarNome = 'térreo';
    } else if (destino === 1) {
      andarNome = 'primeiro andar';
    } else if (destino === 2) {
      andarNome = 'segundo andar';
    } else if (destino === 3) {
      andarNome = 'terceiro andar';
    }

    // Salva na sessão para aguardar confirmação
    const sessionAttributes = handlerInput.attributesManager.getSessionAttributes();
    sessionAttributes.pendingCommand = 'CALL_ELEVATOR';
    sessionAttributes.targetFloor = destino;
    sessionAttributes.targetFloorName = andarNome;
    handlerInput.attributesManager.setSessionAttributes(sessionAttributes);

    return handlerInput.responseBuilder
      .speak(`Confirmando: chamar o elevador para o ${andarNome}. Está correto?`)
      .reprompt('Diga sim para confirmar ou não para cancelar.')
      .withShouldEndSession(false)
      .getResponse();
  }
};

/**
 * Handler para ConfirmarSimIntent - confirma comandos pendentes
 */
const YesIntentHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
      && (Alexa.getIntentName(handlerInput.requestEnvelope) === 'ConfirmarSimIntent' ||
          Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.YesIntent');
  },
  async handle(handlerInput) {
    const sessionAttributes = handlerInput.attributesManager.getSessionAttributes();

    // Verifica se há comando pendente
    if (!sessionAttributes.pendingCommand) {
      return handlerInput.responseBuilder
        .speak('Não há nenhum comando pendente. Como posso ajudar?')
        .reprompt('Você pode pedir para chamar o elevador ou ir para outro andar.')
        .getResponse();
    }

    if (sessionAttributes.pendingCommand === 'CALL_ELEVATOR') {
      const destino = sessionAttributes.targetFloor;
      const andarNome = sessionAttributes.targetFloorName;

      // Limpa comando pendente
      delete sessionAttributes.pendingCommand;
      delete sessionAttributes.targetFloor;
      delete sessionAttributes.targetFloorName;
      handlerInput.attributesManager.setSessionAttributes(sessionAttributes);

      try {
        console.log(`[CONFIRMADO] Processando comando CALL_ELEVATOR para o andar ${destino}...`);

      // Busca o andar atual do app (ONDE A PESSOA ESTÁ)
      const currentFloorSnapshot = await db.ref('user_status/default_user/current_floor').once('value');
      const appCurrentFloor = currentFloorSnapshot.val();

      console.log(`Andar onde a pessoa está (app): ${appCurrentFloor !== null ? appCurrentFloor : 'não disponível'}`);
      console.log(`Andar de destino (Alexa): ${destino}`);

      // Valida se o app conhece o andar atual
      if (appCurrentFloor === null || appCurrentFloor < 0) {
        console.error('❌ App não detectou o andar atual');
        return handlerInput.responseBuilder
          .speak('Desculpe, o aplicativo não conseguiu detectar em qual andar você está. Por favor, configure o andar manualmente no app.')
          .getResponse();
      }

      // Verifica se já está no andar de destino
      if (appCurrentFloor === destino) {
        return handlerInput.responseBuilder
          .speak('Você já está neste andar!')
          .getResponse();
      }

      // Gera ID único para o comando
      const commandId = db.ref('commands').push().key;

      // Busca o token FCM do app Android
      const tokenSnapshot = await db.ref('fcm_tokens/default_user').once('value');
      const fcmToken = tokenSnapshot.val();

      if (!fcmToken) {
        console.error('❌ Token FCM não encontrado no Firebase');
        return handlerInput.responseBuilder
          .speak('Desculpe, o aplicativo não está conectado. Por favor, abra o app Elevox primeiro.')
          .getResponse();
      }

      console.log(`✓ Enviando comando: currentFloor=${appCurrentFloor}, targetFloor=${destino}`);

      // Envia push notification via FCM
      await admin.messaging().send({
        token: fcmToken,
        data: {
          commandId: commandId,
          type: 'CALL_ELEVATOR',
          currentFloor: appCurrentFloor.toString(),
          targetFloor: destino.toString(),
          timestamp: Date.now().toString()
        }
      });

      console.log('✅ Push notification enviada com sucesso');

      const speakOutput = `Certo! Chamando o elevador para o ${andarNome}.`;

      return handlerInput.responseBuilder
        .speak(speakOutput)
        .getResponse();
      } catch (error) {
        console.error('Erro ao enviar comando:', error);
        console.error('Stack trace:', error.stack);
        return handlerInput.responseBuilder
          .speak('Desculpe, ocorreu um erro ao processar seu pedido. Por favor, tente novamente.')
          .getResponse();
      }
    }

    // Comando GO_TO_FLOOR será tratado depois
    return handlerInput.responseBuilder
      .speak('Comando não reconhecido.')
      .getResponse();
  }
};

/**
 * Handler para ConfirmarNaoIntent - cancela comandos pendentes
 */
const NoIntentHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
      && (Alexa.getIntentName(handlerInput.requestEnvelope) === 'ConfirmarNaoIntent' ||
          Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.NoIntent');
  },
  handle(handlerInput) {
    const sessionAttributes = handlerInput.attributesManager.getSessionAttributes();

    // Limpa comando pendente
    delete sessionAttributes.pendingCommand;
    delete sessionAttributes.targetFloor;
    delete sessionAttributes.targetFloorName;
    delete sessionAttributes.currentFloor;
    delete sessionAttributes.currentFloorName;
    handlerInput.attributesManager.setSessionAttributes(sessionAttributes);

    return handlerInput.responseBuilder
      .speak('Comando cancelado. Como posso ajudar?')
      .reprompt('Você pode pedir para chamar o elevador ou ir para outro andar.')
      .getResponse();
  }
};

/**
 * Handler para ir para um andar específico
 * "Alexa, pede Elevox para ir do andar 2 para o andar 5"
 */
const IrParaAndarIntentHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
      && Alexa.getIntentName(handlerInput.requestEnvelope) === 'IrParaAndarIntent';
  },
  async handle(handlerInput) {
    const intentRequest = handlerInput.requestEnvelope.request;
    const confirmationStatus = intentRequest.intent.confirmationStatus;
    const slots = intentRequest.intent.slots;
    const andarAtualSlot = slots.andar_atual;
    const andarDestinoSlot = slots.andar_destino;

    // Verifica se o usuário negou a confirmação
    if (confirmationStatus === 'DENIED') {
      return handlerInput.responseBuilder
        .speak('Comando cancelado. Como posso ajudar?')
        .reprompt('Você pode pedir para chamar o elevador ou ir para outro andar.')
        .getResponse();
    }

    // Se ainda não foi confirmado, pede confirmação manualmente
    if (confirmationStatus !== 'CONFIRMED') {
      // Função auxiliar para extrair ID do slot
      const extrairIdAndar = (andarSlot) => {
        if (andarSlot && andarSlot.resolutions &&
            andarSlot.resolutions.resolutionsPerAuthority &&
            andarSlot.resolutions.resolutionsPerAuthority[0] &&
            andarSlot.resolutions.resolutionsPerAuthority[0].values &&
            andarSlot.resolutions.resolutionsPerAuthority[0].values[0]) {
          return parseInt(andarSlot.resolutions.resolutionsPerAuthority[0].values[0].value.id);
        } else if (andarSlot && andarSlot.value) {
          return parseInt(andarSlot.value);
        }
        return 0;
      };

      // Função auxiliar para formatar nome do andar baseado no ID
      const formatarAndar = (andarId) => {
        if (andarId === 0) return 'térreo';
        if (andarId === 1) return 'primeiro andar';
        if (andarId === 2) return 'segundo andar';
        if (andarId === 3) return 'terceiro andar';
        return 'térreo';
      };

      const atualId = extrairIdAndar(andarAtualSlot);
      const destinoId = extrairIdAndar(andarDestinoSlot);
      const atualNome = formatarAndar(atualId);
      const destinoNome = formatarAndar(destinoId);

      return handlerInput.responseBuilder
        .speak(`Confirmando: ir do ${atualNome} para o ${destinoNome}. Está correto?`)
        .reprompt('Diga sim para confirmar ou não para cancelar.')
        .addConfirmIntentDirective()
        .withShouldEndSession(false)
        .getResponse();
    }

    // Se não forneceu os andares, pede para tentar novamente
    if (!andarAtualSlot?.value || !andarDestinoSlot?.value) {
      return handlerInput.responseBuilder
        .speak('Por favor, me diga de qual andar e para qual andar deseja ir. Por exemplo: ir do térreo para o primeiro andar.')
        .reprompt('Diga: ir do térreo para o segundo, por exemplo.')
        .getResponse();
    }

    // Função auxiliar para extrair o ID do slot
    const getSlotId = (slot) => {
      if (slot.resolutions &&
          slot.resolutions.resolutionsPerAuthority &&
          slot.resolutions.resolutionsPerAuthority[0] &&
          slot.resolutions.resolutionsPerAuthority[0].values &&
          slot.resolutions.resolutionsPerAuthority[0].values[0]) {
        return parseInt(slot.resolutions.resolutionsPerAuthority[0].values[0].value.id);
      }
      return parseInt(slot.value);
    };

    const atual = getSlotId(andarAtualSlot);
    const destino = getSlotId(andarDestinoSlot);

    // Valida os andares (térreo, 1º, 2º ou 3º)
    if (isNaN(atual) || atual < 0 || atual > 3) {
      return handlerInput.responseBuilder
        .speak('Desculpe, o andar atual não é válido. Por favor, diga térreo, primeiro, segundo ou terceiro andar.')
        .getResponse();
    }

    if (isNaN(destino) || destino < 0 || destino > 3) {
      return handlerInput.responseBuilder
        .speak('Desculpe, o andar de destino não é válido. Por favor, diga térreo, primeiro, segundo ou terceiro andar.')
        .getResponse();
    }

    if (atual === destino) {
      return handlerInput.responseBuilder
        .speak('Você já está neste andar!')
        .getResponse();
    }

    try {
      console.log(`Enviando comando GO_TO_FLOOR via FCM do andar ${atual} para o andar ${destino}`);

      // Gera ID único para o comando
      const commandId = db.ref('commands').push().key;

      // Busca o token FCM do app Android
      const tokenSnapshot = await db.ref('fcm_tokens/default_user').once('value');
      const fcmToken = tokenSnapshot.val();

      if (!fcmToken) {
        console.error('❌ Token FCM não encontrado no Firebase');
        return handlerInput.responseBuilder
          .speak('Desculpe, o aplicativo não está conectado. Por favor, abra o app Elevox primeiro.')
          .getResponse();
      }

      console.log('Enviando push notification via FCM...');

      // Envia push notification via FCM
      await admin.messaging().send({
        token: fcmToken,
        data: {
          commandId: commandId,
          type: 'GO_TO_FLOOR',
          currentFloor: atual.toString(),
          targetFloor: destino.toString(),
          timestamp: Date.now().toString()
        }
      });

      console.log('✅ Push notification enviada com sucesso');

      const atualNome = atual === 0 ? 'térreo' : `andar ${atual}`;
      const destinoNome = destino === 0 ? 'térreo' : `andar ${destino}`;
      const speakOutput = `Perfeito! Chamando o elevador do ${atualNome} para o ${destinoNome}.`;

      return handlerInput.responseBuilder
        .speak(speakOutput)
        .getResponse();
    } catch (error) {
      console.error('Erro ao enviar comando:', error);
      console.error('Stack trace:', error.stack);
      return handlerInput.responseBuilder
        .speak('Desculpe, ocorreu um erro ao processar seu pedido. Por favor, tente novamente.')
        .getResponse();
    }
  }
};

/**
 * Handler de ajuda
 */
const HelpIntentHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
      && Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.HelpIntent';
  },
  handle(handlerInput) {
    const speakOutput = 'Você pode me pedir para chamar o elevador dizendo: chama o elevador no andar três. ' +
                       'Ou pode pedir para ir para um andar específico dizendo: ir do andar dois para o andar cinco. ' +
                       'Como posso ajudar?';

    return handlerInput.responseBuilder
      .speak(speakOutput)
      .reprompt(speakOutput)
      .getResponse();
  }
};

/**
 * Handlers de cancelamento e parada
 */
const CancelAndStopIntentHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === 'IntentRequest'
      && (Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.CancelIntent'
        || Alexa.getIntentName(handlerInput.requestEnvelope) === 'AMAZON.StopIntent');
  },
  handle(handlerInput) {
    const speakOutput = 'Até logo!';

    return handlerInput.responseBuilder
      .speak(speakOutput)
      .getResponse();
  }
};

/**
 * Handler de fim de sessão
 */
const SessionEndedRequestHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === 'SessionEndedRequest';
  },
  handle(handlerInput) {
    console.log(`Sessão encerrada: ${JSON.stringify(handlerInput.requestEnvelope)}`);
    return handlerInput.responseBuilder.getResponse();
  }
};

/**
 * Handler de erro genérico
 */
const ErrorHandler = {
  canHandle() {
    return true;
  },
  handle(handlerInput, error) {
    const speakOutput = 'Desculpe, tive problemas para processar seu pedido. Por favor, tente novamente.';
    console.log(`Erro: ${JSON.stringify(error)}`);

    return handlerInput.responseBuilder
      .speak(speakOutput)
      .reprompt(speakOutput)
      .getResponse();
  }
};

/**
 * Skill Builder - exporta o handler
 */
const skillHandler = Alexa.SkillBuilders.custom()
  .addRequestHandlers(
    LaunchRequestHandler,
    YesIntentHandler,
    NoIntentHandler,
    ChamarElevadorIntentHandler,
    IrParaAndarIntentHandler,
    HelpIntentHandler,
    CancelAndStopIntentHandler,
    SessionEndedRequestHandler
  )
  .addErrorHandlers(ErrorHandler)
  .lambda();

// Wrapper para configurar callbackWaitsForEmptyEventLoop
export const handler = (event, context, callback) => {
  // Não espera o event loop esvaziar antes de finalizar
  // Isso permite que o Lambda termine mesmo com conexões Firebase abertas
  context.callbackWaitsForEmptyEventLoop = false;

  console.log('Handler invocado, context.callbackWaitsForEmptyEventLoop =', context.callbackWaitsForEmptyEventLoop);
  console.log('Event type:', event.request?.type);
  console.log('Intent name:', event.request?.intent?.name);

  // Usa Promise para converter callback em async
  return new Promise((resolve, reject) => {
    try {
      skillHandler(event, context, (error, result) => {
        if (error) {
          console.error('Erro ao processar skill handler:', error);
          console.error('Error stack:', error?.stack);
          reject(error);
          if (callback) callback(error);
        } else {
          if (result) {
            const responseStr = JSON.stringify(result);
            console.log('Response gerada com sucesso:', responseStr.substring(0, Math.min(200, responseStr.length)));
          } else {
            console.log('Response is undefined or null');
          }
          resolve(result);
          if (callback) callback(null, result);
        }
      });
    } catch (error) {
      console.error('Erro ao criar promise:', error);
      console.error('Error stack:', error?.stack);
      reject(error);
      if (callback) callback(error);
    }
  });
};
