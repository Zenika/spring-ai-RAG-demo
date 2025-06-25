package com.zenika.demo.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.util.Scanner;

@SpringBootApplication
public class RagDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagDemoApplication.class, args);
    }

    @Value("${rag.document-path:classpath:spring-framework.pdf}")
    String documentPath = "classpath:spring-framework.pdf";

    @Value("${rag.system-prompt:You are a spring framework expert assistant that answers questions based on the provided documents.}")
    String systemPrompt;

    @Bean
    @Order(1)
    ApplicationRunner fillVectorStore(VectorStore vectorStore) {
        return args -> {
            if (args.containsOption("fillVectorStore")) {
                System.out.println("Filling vector store...");
                vectorStore.add(TokenTextSplitter.builder().withChunkSize(500).build().split(new ParagraphPdfDocumentReader(documentPath).read()));
                // Logic to fill the document store
            } else {
                System.out.println("Skipping vector store filling.");
            }
        };
    }

    @Bean
    @Order(2)
    ApplicationRunner cliMainLoop(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, ApplicationContext applicationContext) {
        var chatClient = chatClientBuilder
            .defaultSystem(systemPrompt)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
            .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore)
                .promptTemplate(PromptTemplate.builder()
                    .template("""
                        {query}
                        
                        Context information is below, surrounded by ---------------------
                        
                        ---------------------
                        {question_answer_context}
                        ---------------------
                        
                        Given the context and provided history information and not prior knowledge,
                        reply to the user comment. If the answer is not in the context, inform
                        the user that you can't answer the question.
                        In the reply, avoid to reference code snippets contained in the context information.
                        Do not reformulate the question, just answer it directly.
                        Try to respond with the same language as the question.
                        If the question is not clear, ask for clarification.
                        At the end of your answer, if you have used the context information, give the chapters where the information comes from.
                        If the user wants you to show the sources, then add context information at the end of your answer.
                        """)
                    .build())
                .build())
            .build();
        return args -> {
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("\nAsk a question: ");
                    String question = scanner.nextLine();
                    if (question.equalsIgnoreCase("exit")) {
                        SpringApplication.exit(applicationContext);
                        break;
                    }
                    chatClient.prompt()
                        .user(question)
                        .stream()
                        .content()
                        .doOnNext(System.out::print)
                        .doOnComplete(System.out::println)
                        .blockLast();
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("An error occurred: " + e.getMessage());
            }
        };
    }

}
