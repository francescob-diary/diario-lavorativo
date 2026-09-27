# Motore del modello linguistico locale (llama.cpp) per la dettatura.
#
# llama.cpp non sta nel repository: si scarica alla compilazione, fermato
# a una versione precisa, cosi' la build non cambia da sola nel tempo.
cmake_minimum_required(VERSION 3.22.1)
project(diario_llm C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# llama.cpp viene unito in una libreria condivisa: serve codice rilocabile.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Anche nella versione di prova il motore va compilato ottimizzato:
# senza, il modello sarebbe dieci volte piu' lento.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
add_compile_options(-O3)

include(FetchContent)

# Solo la libreria: niente esempi, test, server, download da internet.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp
    GIT_TAG b6000
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama)

add_library(diario_llm SHARED llama_jni.cpp)
target_link_libraries(diario_llm PRIVATE llama)
if (ANDROID)
    target_link_libraries(diario_llm PRIVATE android log)
    # Pagine da 16 KB: richieste dai telefoni Android piu' recenti.
    target_link_options(diario_llm PRIVATE "-Wl,-z,max-page-size=16384")
endif()
