#!/usr/bin/env python
# -*- coding: UTF-8 -*-
"""
@time: 2026/01/08
@file: powermem.py
@desc: PowerMem memory provider for xiaozhi-esp32-server
       PowerMem is an open-source agent memory component from OceanBase
       GitHub: https://github.com/oceanbase/powermem
       Website: https://www.powermem.ai/
"""

import traceback
from typing import Optional, Dict, Any

from ..base import MemoryProviderBase, logger
from powermem import AsyncMemory

TAG = __name__


class MemoryProvider(MemoryProviderBase):
    """
    PowerMem memory provider implementation.
    
    PowerMem is an open-source agent memory component that provides
    efficient memory management for AI agents.
    
    Supports multiple storage backends (sqlite, oceanbase, postgres),
    LLM providers (qwen, openai, etc.) and embedding providers.
    """

    def __init__(self, config: Dict[str, Any], summary_memory: Optional[str] = None):
        super().__init__(config)
        self.use_powermem = False
        self.memory_client = None
        
        try:            
            # Get configuration parameters
            database_provider = config.get("database_provider", "sqlite")
            llm_provider = config.get("llm_provider", "qwen")
            embedding_provider = config.get("embedding_provider", "qwen")
            
            # Build powermem configuration dict
            # PowerMem supports two config styles:
            # 1. powermem style: database, llm, embedding
            # 2. mem0 style: vector_store, llm, embedder
            powermem_config = {}
            
            # Configure vector store / database
            if "vector_store" in config:
                powermem_config["vector_store"] = config["vector_store"]
            elif "database" in config:
                powermem_config["database"] = config["database"]
            else:
                powermem_config["vector_store"] = {
                    "provider": database_provider,
                    "config": {}
                }
            
            # Configure LLM
            if "llm" in config:
                powermem_config["llm"] = config["llm"]
            else:
                llm_config = {}
                if "llm_api_key" in config:
                    llm_config["api_key"] = config["llm_api_key"]
                if "llm_model" in config:
                    llm_config["model"] = config["llm_model"]
                if "llm_base_url" in config:
                    llm_config["base_url"] = config["llm_base_url"]
                powermem_config["llm"] = {
                    "provider": llm_provider,
                    "config": llm_config
                }
            
            # Configure embedder
            if "embedder" in config:
                powermem_config["embedder"] = config["embedder"]
            else:
                embedder_config = {}
                if "embedding_api_key" in config:
                    embedder_config["api_key"] = config["embedding_api_key"]
                if "embedding_model" in config:
                    embedder_config["model"] = config["embedding_model"]
                if "embedding_base_url" in config:
                    embedder_config["base_url"] = config["embedding_base_url"]
                powermem_config["embedder"] = {
                    "provider": embedding_provider,
                    "config": embedder_config
                }
            
            # Initialize AsyncMemory client
            self.memory_client = AsyncMemory(config=powermem_config)
            self.use_powermem = True
            
            logger.bind(tag=TAG).info(
                f"PowerMem initialized successfully with database={database_provider}, "
                f"llm={llm_provider}, embedding={embedding_provider}"
            )
            
        except ImportError as e:
            logger.bind(tag=TAG).error(
                f"PowerMem not installed. Please install with: pip install powermem. Error: {e}"
            )
            self.use_powermem = False
        except Exception as e:
            logger.bind(tag=TAG).error(f"Failed to initialize PowerMem: {str(e)}")
            logger.bind(tag=TAG).debug(f"Detailed error: {traceback.format_exc()}")
            self.use_powermem = False

    async def save_memory(self, msgs):
        """
        Save conversation messages to PowerMem.
        
        Args:
            msgs: List of message objects with 'role' and 'content' attributes
            
        Returns:
            Result from PowerMem API or None if failed
        """
        if not self.use_powermem or self.memory_client is None:
            logger.bind(tag=TAG).warning("PowerMem is not available, skipping save_memory")
            return None
            
        if len(msgs) < 2:
            logger.bind(tag=TAG).debug("Not enough messages to save (need at least 2)")
            return None

        try:
            # Format the content as a message list for PowerMem
            messages = [
                {"role": message.role, "content": message.content}
                for message in msgs
                if message.role != "system"
            ]
            
            # Add memory using PowerMem SDK
            result = await self.memory_client.add(
                messages=messages,
                user_id=self.role_id
            )
            
            logger.bind(tag=TAG).debug(f"Save memory result: {result}")
            return result
                        
        except Exception as e:
            logger.bind(tag=TAG).error(f"Error saving memory: {str(e)}")
            logger.bind(tag=TAG).debug(f"Detailed error: {traceback.format_exc()}")
            return None

    async def query_memory(self, query: str) -> str:
        """
        Query memories from PowerMem based on similarity search.
        
        Args:
            query: The search query string
            
        Returns:
            Formatted string of relevant memories or empty string if none found
        """
        if not self.use_powermem or self.memory_client is None:
            logger.bind(tag=TAG).warning("PowerMem is not available, skipping query_memory")
            return ""
            
        try:
            if not getattr(self, "role_id", None):
                logger.bind(tag=TAG).debug("No role_id set, returning empty memory")
                return ""

            # Search memories using PowerMem SDK
            results = await self.memory_client.search(
                query=query,
                user_id=self.role_id,
                limit=30
            )
                    
            if not results or "results" not in results:
                logger.bind(tag=TAG).debug("No memory results found")
                return ""

            # Format each memory entry with its update time
            memories = []
            for entry in results.get("results", []):
                # Get timestamp from updated_at or created_at
                timestamp = ""
                if "updated_at" in entry and entry["updated_at"]:
                    timestamp = str(entry["updated_at"])
                elif "created_at" in entry and entry["created_at"]:
                    timestamp = str(entry["created_at"])
                    
                if timestamp:
                    try:
                        # Parse and reformat the timestamp (remove milliseconds if present)
                        if "." in timestamp:
                            dt = timestamp.split(".")[0]
                        else:
                            dt = timestamp
                        formatted_time = dt.replace("T", " ")
                    except Exception:
                        formatted_time = timestamp
                else:
                    formatted_time = ""
                    
                memory = entry.get("memory", "") or entry.get("content", "")
                if memory:
                    if formatted_time:
                        # Store tuple of (timestamp, formatted_string) for sorting
                        memories.append((timestamp, f"[{formatted_time}] {memory}"))
                    else:
                        memories.append(("", memory))

            # Sort by timestamp in descending order (newest first)
            memories.sort(key=lambda x: x[0], reverse=True)

            # Extract only the formatted strings
            memories_str = "\n".join(f"- {memory[1]}" for memory in memories)
            logger.bind(tag=TAG).debug(f"Query results: {memories_str}")
            return memories_str
            
        except Exception as e:
            logger.bind(tag=TAG).error(f"Error querying memory: {str(e)}")
            logger.bind(tag=TAG).debug(f"Detailed error: {traceback.format_exc()}")
            return ""


# Register the memory provider instance
powermem = MemoryProvider({})
