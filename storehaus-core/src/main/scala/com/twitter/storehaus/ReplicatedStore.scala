/*
 * Copyright 2013 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.storehaus

import FutureOps.{ selectFirstSuccessfulTrial => selectFirst }
import CollectionOps.combineMaps
import com.twitter.util.Future

/** Replicates reads to a seq of stores and returns the first successful value (empty or not)
 */
class ReplicatedReadableStore[-K, +V](stores: Seq[ReadableStore[K, V]])(pred: Option[V] => Boolean)
    extends AbstractReadableStore[K, V] {
  override def get(k: K): Future[Option[V]] = selectFirst(stores.map { _.get(k) })(pred)
  override def multiGet[K1 <: K](ks: Set[K1]): Map[K1, Future[Option[V]]] =
    combineMaps(stores.map { _.multiGet(ks) }).mapValues { selectFirst(_)(pred) }
}

/**
 * Replicates writes to all stores, and takes the first successful read.
 */
class ReplicatedStore[-K, V](
    stores: Seq[Store[K, V]])(pred: Option[V] => Boolean)(implicit collect: FutureCollector)
    extends ReplicatedReadableStore[K, V](stores)(pred) with Store[K, V] {
  override def put(kv: (K, Option[V])): Future[Unit] =
    collect(stores.map { _.put(kv) }).map { _ => () }
  override def multiPut[K1 <: K](kvs: Map[K1, Option[V]]): Map[K1, Future[Unit]] =
    combineMaps(stores.map { _.multiPut(kvs) })
      .mapValues { seqf => collect(seqf).map { _ => () } }
}
