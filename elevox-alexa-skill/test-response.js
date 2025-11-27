// Teste simples para verificar se a resposta da Lambda está correta
import Alexa from 'ask-sdk-core';

// Simula um request da Alexa
const testEvent = {
  version: "1.0",
  session: {
    new: true,
    sessionId: "test-session",
    application: { applicationId: "amzn1.ask.skill.test" },
    attributes: {},
    user: { userId: "test-user" }
  },
  context: {
    System: {
      application: { applicationId: "amzn1.ask.skill.test" },
      user: { userId: "test-user" },
      device: { deviceId: "test-device" }
    }
  },
  request: {
    type: "LaunchRequest",
    requestId: "test-request",
    timestamp: new Date().toISOString(),
    locale: "pt-BR"
  }
};

// Cria um handler minimalista
const LaunchRequestHandler = {
  canHandle(handlerInput) {
    return Alexa.getRequestType(handlerInput.requestEnvelope) === 'LaunchRequest';
  },
  handle(handlerInput) {
    console.log('Handler executado');
    const response = handlerInput.responseBuilder
      .speak('Teste de resposta da Alexa. Se você ouvir isso, está funcionando.')
      .reprompt('Diga algo para continuar.')
      .getResponse();

    console.log('Response gerada:', JSON.stringify(response, null, 2));
    return response;
  }
};

const skillHandler = Alexa.SkillBuilders.custom()
  .addRequestHandlers(LaunchRequestHandler)
  .lambda();

// Testa
skillHandler(testEvent, { callbackWaitsForEmptyEventLoop: false }, (error, result) => {
  if (error) {
    console.error('ERRO:', error);
  } else {
    console.log('\n✅ SUCESSO! Response:');
    console.log(JSON.stringify(result, null, 2));

    // Verifica se tem outputSpeech
    if (result.response && result.response.outputSpeech) {
      console.log('\n✅ outputSpeech presente:', result.response.outputSpeech.ssml || result.response.outputSpeech.text);
    } else {
      console.log('\n❌ PROBLEMA: outputSpeech ausente!');
    }
  }
});
